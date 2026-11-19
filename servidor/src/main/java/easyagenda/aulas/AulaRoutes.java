package easyagenda.aulas;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.bson.Document;
import org.bson.types.ObjectId;

import com.mongodb.ErrorCategory;
import com.mongodb.MongoWriteException;
import com.mongodb.client.model.Filters;
import com.sun.net.httpserver.HttpExchange;

import easyagenda.Json;
import easyagenda.Server;
import easyagenda.auth.Usuarios;
import easyagenda.horarios.Horarios;

/**
 * Rotas das aulas: agendamento (tarefa 3.1), cancelar e remarcar (tarefa 4.1),
 * próximas aulas e histórico (tarefa 4.3).
 */
public final class AulaRoutes {

    /** Maior prazo que o professor pode pedir: 30 dias. */
    private static final int PRAZO_MAXIMO_HORAS = 720;

    private AulaRoutes() {
    }

    /**
     * POST em /aulas e nos caminhos abaixo dele (só aluno):
     * /aulas, /aulas/{id}/cancelar e /aulas/{id}/remarcar.
     */
    public static void postar(HttpExchange exchange) throws IOException {
        String caminho = exchange.getRequestURI().getPath();
        if ("/aulas".equals(caminho)) {
            agendar(exchange);
            return;
        }
        // caminho esperado: /aulas/{id}/cancelar ou /aulas/{id}/remarcar
        String[] partes = caminho.split("/");
        if (partes.length == 4 && "cancelar".equals(partes[3])) {
            cancelar(exchange, partes[2]);
        } else if (partes.length == 4 && "remarcar".equals(partes[3])) {
            remarcar(exchange, partes[2]);
        } else {
            Json.erro(exchange, 404, "rota não encontrada");
        }
    }

    /**
     * POST /aulas  (só aluno)
     * Recebe: {"horarioId"} — um dos horários de GET /professores/{id}/horarios.
     * 201 com a aula marcada. 400 dado inválido ou horário que já passou,
     * 404 horário inexistente, 409 "horário indisponível" se alguém marcou antes.
     */
    private static void agendar(HttpExchange exchange) throws IOException {
        Document horario = horarioDoCorpo(exchange);
        if (horario == null) {
            return; // horarioDoCorpo já respondeu o erro
        }
        ObjectId alunoId = new ObjectId(Server.usuarioLogado(exchange).id());
        Document aula = marcar(exchange, horario, alunoId);
        if (aula != null) {
            Json.enviar(exchange, 201, Aulas.paraJson(aula, horario));
        }
    }

    /**
     * POST /aulas/{id}/cancelar  (só o aluno da aula)
     * 200 com a aula cancelada. O horário volta a ficar livre para outros alunos.
     * 404 aula inexistente ou de outro aluno, 409 se a aula não está mais
     * agendada, já começou ou se falta menos tempo que o prazo do professor.
     */
    private static void cancelar(HttpExchange exchange, String aulaId) throws IOException {
        Document aula = aulaQuePodeMudar(exchange, aulaId, "cancelar");
        if (aula == null) {
            return;
        }
        if (!Aulas.cancelar(aula.getObjectId("_id"))) {
            Json.erro(exchange, 409, "essa aula não está mais agendada");
            return;
        }
        Document cancelada = Aulas.buscarPorId(aula.getObjectId("_id"));
        Json.enviar(exchange, 200, Aulas.paraJson(cancelada, Aulas.horarioDaAula(cancelada)));
    }

    /**
     * POST /aulas/{id}/remarcar  (só o aluno da aula)
     * Recebe: {"horarioId"} — outro horário livre do mesmo professor.
     * Agenda a aula nova (mesmas regras da 3.1) e cancela a antiga.
     * 201 com a aula nova e "remarcadaDe" (o id da aula antiga).
     * Se o horário novo estiver ocupado (409), a aula antiga continua marcada.
     */
    private static void remarcar(HttpExchange exchange, String aulaId) throws IOException {
        Document antiga = aulaQuePodeMudar(exchange, aulaId, "remarcar");
        if (antiga == null) {
            return;
        }
        Document horario = horarioDoCorpo(exchange);
        if (horario == null) {
            return;
        }
        if (!horario.getObjectId("professorId").equals(antiga.getObjectId("professorId"))) {
            Json.erro(exchange, 400, "escolha um horário do mesmo professor da aula");
            return;
        }
        if (Horarios.inicio(horario).equals(antiga.getDate("dataHora"))) {
            Json.erro(exchange, 400, "esse já é o horário da sua aula");
            return;
        }

        // Primeiro marca a nova: se o horário estiver ocupado, nada muda na antiga.
        Document nova = marcar(exchange, horario, antiga.getObjectId("alunoId"));
        if (nova == null) {
            return;
        }
        if (!Aulas.cancelar(antiga.getObjectId("_id"))) {
            // A antiga foi cancelada por outra requisição no meio do caminho: desfaz a nova.
            Aulas.apagar(nova.getObjectId("_id"));
            Json.erro(exchange, 409, "essa aula não está mais agendada");
            return;
        }
        Json.enviar(exchange, 201, Aulas.paraJson(nova, horario)
                .append("remarcadaDe", antiga.getObjectId("_id").toHexString()));
    }

    /**
     * GET /professor/prazo-cancelamento  (só professor)
     * Devolve {"prazoCancelamentoHoras"}: com quantas horas de antecedência,
     * no mínimo, o aluno pode cancelar ou remarcar. Padrão: 24.
     */
    public static void verPrazo(HttpExchange exchange) throws IOException {
        if (!"/professor/prazo-cancelamento".equals(exchange.getRequestURI().getPath())) {
            Json.erro(exchange, 404, "rota não encontrada");
            return;
        }
        ObjectId professorId = new ObjectId(Server.usuarioLogado(exchange).id());
        Json.enviar(exchange, 200, new Document("prazoCancelamentoHoras", Usuarios.prazoCancelamentoHoras(professorId)));
    }

    /**
     * PUT /professor/prazo-cancelamento  (só professor)
     * Recebe: {"prazoCancelamentoHoras": 24} — número inteiro de 0 a 720 (30 dias).
     * 0 quer dizer que o aluno pode cancelar até a aula começar.
     */
    public static void mudarPrazo(HttpExchange exchange) throws IOException {
        if (!"/professor/prazo-cancelamento".equals(exchange.getRequestURI().getPath())) {
            Json.erro(exchange, 404, "rota não encontrada");
            return;
        }
        Document corpo = Json.lerCorpo(exchange);
        Object valor = corpo == null ? null : corpo.get("prazoCancelamentoHoras");
        if (!(valor instanceof Number numero) || numero.doubleValue() != Math.floor(numero.doubleValue())
                || numero.doubleValue() < 0 || numero.doubleValue() > PRAZO_MAXIMO_HORAS) {
            Json.erro(exchange, 400, "envie prazoCancelamentoHoras como um número inteiro de 0 a " + PRAZO_MAXIMO_HORAS);
            return;
        }
        int horas = numero.intValue();
        Usuarios.definirPrazoCancelamento(new ObjectId(Server.usuarioLogado(exchange).id()), horas);
        Json.enviar(exchange, 200, new Document("prazoCancelamentoHoras", horas));
    }

    /**
     * GET /aulas/proximas  (qualquer usuário logado)
     * Aulas agendadas que ainda não começaram, da mais próxima para a mais distante.
     */
    public static void listarProximas(HttpExchange exchange) throws IOException {
        if (!"/aulas/proximas".equals(exchange.getRequestURI().getPath())) {
            Json.erro(exchange, 404, "rota não encontrada");
            return;
        }
        Json.enviar(exchange, 200, new Document("aulas", paraJson(Aulas.proximas(Server.usuarioLogado(exchange)))));
    }

    /**
     * GET /aulas/historico  (qualquer usuário logado)
     * Aulas que já passaram e aulas canceladas, da mais recente para a mais antiga.
     */
    public static void listarHistorico(HttpExchange exchange) throws IOException {
        if (!"/aulas/historico".equals(exchange.getRequestURI().getPath())) {
            Json.erro(exchange, 404, "rota não encontrada");
            return;
        }
        Json.enviar(exchange, 200, new Document("aulas", paraJson(Aulas.historico(Server.usuarioLogado(exchange)))));
    }

    // ----- ajudantes -----

    /**
     * Lê {"horarioId"} do corpo e confere se o horário existe e ainda não
     * passou (regras da 3.1). Responde o erro e devolve null se não der.
     */
    private static Document horarioDoCorpo(HttpExchange exchange) throws IOException {
        Document corpo = Json.lerCorpo(exchange);
        String horarioId = corpo == null ? null : Json.texto(corpo, "horarioId");
        if (horarioId == null) {
            Json.erro(exchange, 400, "envie um JSON com horarioId");
            return null;
        }
        Document horario = ObjectId.isValid(horarioId) ? Horarios.buscarPorId(new ObjectId(horarioId)) : null;
        if (horario == null) {
            Json.erro(exchange, 404, "horário não encontrado");
            return null;
        }
        if (Horarios.jaPassou(horario)) {
            Json.erro(exchange, 400, "esse horário já passou");
            return null;
        }
        return horario;
    }

    /**
     * Marca a aula no horário. Não consultamos antes se está livre: o índice
     * único do banco é quem garante, inclusive quando dois alunos confirmam ao
     * mesmo tempo. Responde 409 e devolve null se o horário já tem aula.
     */
    private static Document marcar(HttpExchange exchange, Document horario, ObjectId alunoId) throws IOException {
        try {
            return Aulas.agendar(horario, alunoId);
        } catch (MongoWriteException e) {
            if (e.getError().getCategory() == ErrorCategory.DUPLICATE_KEY) {
                Json.erro(exchange, 409, "horário indisponível");
                return null;
            }
            throw e;
        }
    }

    /**
     * Busca a aula do aluno logado e confere se ela ainda pode ser cancelada ou
     * remarcada: está agendada, não começou e falta mais tempo que o prazo do
     * professor. Responde o erro e devolve null se não puder.
     */
    private static Document aulaQuePodeMudar(HttpExchange exchange, String aulaId, String acao) throws IOException {
        Document aula = ObjectId.isValid(aulaId) ? Aulas.buscarPorId(new ObjectId(aulaId)) : null;
        String alunoId = Server.usuarioLogado(exchange).id();
        if (aula == null || !aula.getObjectId("alunoId").toHexString().equals(alunoId)) {
            Json.erro(exchange, 404, "aula não encontrada");
            return null;
        }
        if (!Aulas.AGENDADA.equals(aula.getString("status"))) {
            Json.erro(exchange, 409, "essa aula não está mais agendada");
            return null;
        }
        Duration falta = Duration.between(Instant.now(), aula.getDate("dataHora").toInstant());
        if (!falta.isPositive()) {
            Json.erro(exchange, 409, "essa aula já começou");
            return null;
        }
        int prazo = Usuarios.prazoCancelamentoHoras(aula.getObjectId("professorId"));
        if (falta.compareTo(Duration.ofHours(prazo)) <= 0) {
            Json.erro(exchange, 409, "não dá mais para " + acao + ": o professor pede pelo menos "
                    + prazo + " h de antecedência");
            return null;
        }
        return aula;
    }

    /** Aulas no formato do site, com o nome do professor e do aluno para a tela mostrar. */
    private static List<Document> paraJson(List<Document> aulas) {
        List<Document> resposta = new ArrayList<>();
        for (Document aula : aulas) {
            resposta.add(Aulas.paraJson(aula, Aulas.horarioDaAula(aula))
                    .append("professorNome", nomeDoUsuario(aula.getObjectId("professorId")))
                    .append("alunoNome", nomeDoUsuario(aula.getObjectId("alunoId"))));
        }
        return resposta;
    }

    private static String nomeDoUsuario(ObjectId id) {
        Document usuario = Usuarios.colecao().find(Filters.eq("_id", id)).first();
        return usuario == null ? null : usuario.getString("nome");
    }
}
