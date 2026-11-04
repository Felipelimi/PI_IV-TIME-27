package easyagenda.aulas;

import java.io.IOException;

import org.bson.Document;
import org.bson.types.ObjectId;

import com.mongodb.ErrorCategory;
import com.mongodb.MongoWriteException;
import com.sun.net.httpserver.HttpExchange;

import easyagenda.Json;
import easyagenda.Server;
import easyagenda.horarios.Horarios;

/**
 * Rotas de agendamento de aula (tarefa 3.1).
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
}
