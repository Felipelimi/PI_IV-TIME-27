package easyagenda.sockets;

import java.time.Duration;
import java.time.Instant;

import org.bson.Document;
import org.bson.types.ObjectId;

import com.mongodb.ErrorCategory;
import com.mongodb.MongoWriteException;

import easyagenda.aulas.Aulas;
import easyagenda.auth.Usuarios;
import easyagenda.horarios.Horarios;

/**
 * Regras de agendar (tarefa 3.1), cancelar e remarcar (tarefa 4.1) aulas.
 * Rodam no servidor de sockets: a SupervisoraDeConexao chama estes métodos
 * para cada pedido que chega e devolve o Resultado ao cliente.
 *
 * Quem pede já foi identificado pela API (token conferido lá); aqui chegam
 * só os ids do usuário logado.
 */
public final class OperacoesDeAula {

    private OperacoesDeAula() {
    }

    /**
     * Aluno marca uma aula no horário livre.
     * 201 com a aula marcada. 400 sem horarioId ou horário que já passou,
     * 404 horário inexistente, 409 "horário indisponível" se alguém marcou antes.
     */
    public static Resultado agendar(PedidoDeAgendamento pedido) {
        try {
            Document horario = horarioLivre(pedido.getHorarioId());
            Document aula = marcar(horario, new ObjectId(pedido.getAlunoId()));
            return Resultado.ok(201, Aulas.paraJson(aula, horario));
        } catch (Recusa recusa) {
            return recusa.resultado;
        }
    }

    /**
     * Aluno ou professor da aula a cancela. O horário volta a ficar livre.
     * 200 com a aula cancelada. 404 aula inexistente ou de outro usuário,
     * 409 se a aula não está mais agendada, já começou ou se falta menos
     * tempo que o prazo do professor.
     */
    public static Resultado cancelar(PedidoDeCancelamento pedido) {
        try {
            Document aula = aulaQuePodeMudar(pedido.getAulaId(), pedido.getUsuarioId(),
                    pedido.getTipoUsuario(), "cancelar");
            if (!Aulas.cancelar(aula.getObjectId("_id"))) {
                throw new Recusa(409, "essa aula não está mais agendada");
            }
            Document cancelada = Aulas.buscarPorId(aula.getObjectId("_id"));
            return Resultado.ok(200, Aulas.paraJson(cancelada, Aulas.horarioDaAula(cancelada)));
        } catch (Recusa recusa) {
            return recusa.resultado;
        }
    }

    /**
     * Aluno ou professor da aula a passa para outro horário livre do mesmo professor.
     * Agenda a aula nova (mesmas regras do agendamento) e cancela a antiga.
     * 201 com a aula nova e "remarcadaDe" (o id da aula antiga).
     * Se o horário novo estiver ocupado (409), a aula antiga continua marcada.
     */
    public static Resultado remarcar(PedidoDeRemarcacao pedido) {
        try {
            Document antiga = aulaQuePodeMudar(pedido.getAulaId(), pedido.getUsuarioId(),
                    pedido.getTipoUsuario(), "remarcar");
            Document horario = horarioLivre(pedido.getHorarioId());
            if (!horario.getObjectId("professorId").equals(antiga.getObjectId("professorId"))) {
                throw new Recusa(400, "escolha um horário do mesmo professor da aula");
            }
            if (Horarios.inicio(horario).equals(antiga.getDate("dataHora"))) {
                throw new Recusa(400, "esse já é o horário da sua aula");
            }

            // Primeiro marca a nova: se o horário estiver ocupado, nada muda na antiga.
            Document nova = marcar(horario, antiga.getObjectId("alunoId"));
            if (!Aulas.cancelar(antiga.getObjectId("_id"))) {
                // A antiga foi cancelada por outro pedido no meio do caminho: desfaz a nova.
                Aulas.apagar(nova.getObjectId("_id"));
                throw new Recusa(409, "essa aula não está mais agendada");
            }
            return Resultado.ok(201, Aulas.paraJson(nova, horario)
                    .append("remarcadaDe", antiga.getObjectId("_id").toHexString()));
        } catch (Recusa recusa) {
            return recusa.resultado;
        }
    }

    // ----- ajudantes -----

    /** Pedido que não pode ser atendido: carrega o Resultado de erro que volta ao cliente. */
    private static final class Recusa extends Exception {

        private static final long serialVersionUID = 1L;

        private final transient Resultado resultado;

        Recusa(int codigo, String mensagem) {
            super(mensagem, null, false, false);
            this.resultado = Resultado.erro(codigo, mensagem);
        }
    }

    /** Confere se o horário existe e ainda não passou. */
    private static Document horarioLivre(String horarioId) throws Recusa {
        if (horarioId == null) {
            throw new Recusa(400, "envie um JSON com horarioId");
        }
        Document horario = ObjectId.isValid(horarioId) ? Horarios.buscarPorId(new ObjectId(horarioId)) : null;
        if (horario == null) {
            throw new Recusa(404, "horário não encontrado");
        }
        if (Horarios.jaPassou(horario)) {
            throw new Recusa(400, "esse horário já passou");
        }
        return horario;
    }

    /**
     * Marca a aula no horário. Não consultamos antes se está livre: o índice
     * único do banco é quem garante, inclusive quando dois alunos confirmam ao
     * mesmo tempo (cada um na sua SupervisoraDeConexao).
     */
    private static Document marcar(Document horario, ObjectId alunoId) throws Recusa {
        try {
            return Aulas.agendar(horario, alunoId);
        } catch (MongoWriteException e) {
            if (e.getError().getCategory() == ErrorCategory.DUPLICATE_KEY) {
                throw new Recusa(409, "horário indisponível");
            }
            throw e;
        }
    }

    /**
     * Busca a aula do participante e confere se ela ainda pode ser cancelada ou
     * remarcada: está agendada, não começou e falta mais tempo que o prazo do professor.
     */
    private static Document aulaQuePodeMudar(String aulaId, String usuarioId, String tipoUsuario, String acao)
            throws Recusa {
        Document aula = aulaId != null && ObjectId.isValid(aulaId) ? Aulas.buscarPorId(new ObjectId(aulaId)) : null;
        String campo = Usuarios.PROFESSOR.equals(tipoUsuario) ? "professorId" : "alunoId";
        if (aula == null || !aula.getObjectId(campo).toHexString().equals(usuarioId)) {
            throw new Recusa(404, "aula não encontrada");
        }
        if (!Aulas.AGENDADA.equals(aula.getString("status"))) {
            throw new Recusa(409, "essa aula não está mais agendada");
        }
        Duration falta = Duration.between(Instant.now(), aula.getDate("dataHora").toInstant());
        if (!falta.isPositive()) {
            throw new Recusa(409, "essa aula já começou");
        }
        int prazo = Usuarios.prazoCancelamentoHoras(aula.getObjectId("professorId"));
        if (falta.compareTo(Duration.ofHours(prazo)) <= 0) {
            throw new Recusa(409, "não dá mais para " + acao + ": o professor pede pelo menos "
                    + prazo + " h de antecedência");
        }
        return aula;
    }
}
