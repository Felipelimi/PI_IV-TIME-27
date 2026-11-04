package easyagenda.aulas;

import java.io.IOException;
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
 * Rotas das aulas: agendamento (tarefa 3.1), próximas aulas e histórico (tarefa 4.3).
 */
public final class AulaRoutes {

    private AulaRoutes() {
    }

    /**
     * POST /aulas  (só aluno)
     * Recebe: {"horarioId"} — um dos horários de GET /professores/{id}/horarios.
     * 201 com a aula marcada. 400 dado inválido ou horário que já passou,
     * 404 horário inexistente, 409 "horário indisponível" se alguém marcou antes.
     */
    public static void agendar(HttpExchange exchange) throws IOException {
        if (!"/aulas".equals(exchange.getRequestURI().getPath())) {
            Json.erro(exchange, 404, "rota não encontrada");
            return;
        }
        Document corpo = Json.lerCorpo(exchange);
        String horarioId = corpo == null ? null : Json.texto(corpo, "horarioId");
        if (horarioId == null) {
            Json.erro(exchange, 400, "envie um JSON com horarioId");
            return;
        }
        Document horario = ObjectId.isValid(horarioId) ? Horarios.buscarPorId(new ObjectId(horarioId)) : null;
        if (horario == null) {
            Json.erro(exchange, 404, "horário não encontrado");
            return;
        }
        if (Horarios.jaPassou(horario)) {
            Json.erro(exchange, 400, "esse horário já passou");
            return;
        }

        ObjectId alunoId = new ObjectId(Server.usuarioLogado(exchange).id());
        Document aula;
        try {
            aula = Aulas.agendar(horario, alunoId);
        } catch (MongoWriteException e) {
            // Não consultamos antes se está livre: o índice único do banco é quem
            // garante, inclusive quando dois alunos confirmam ao mesmo tempo.
            if (e.getError().getCategory() == ErrorCategory.DUPLICATE_KEY) {
                Json.erro(exchange, 409, "horário indisponível");
                return;
            }
            throw e;
        }

        Json.enviar(exchange, 201, Aulas.paraJson(aula, horario));
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
