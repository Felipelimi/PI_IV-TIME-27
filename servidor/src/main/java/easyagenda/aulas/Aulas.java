package easyagenda.aulas;

import java.util.List;

import org.bson.Document;
import org.bson.types.ObjectId;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;

import easyagenda.Database;
import easyagenda.horarios.Horarios;

/**
 * Tudo que mexe na coleção "aulas" fica aqui.
 */
public final class Aulas {

    public static final String AGENDADA = "agendada";
    public static final String REALIZADA = "realizada";

    private Aulas() {
    }

    public static MongoCollection<Document> colecao() {
        return Database.get().getCollection("aulas");
    }

    /**
     * Garante o índice que impede duas aulas ativas do mesmo professor no mesmo
     * horário (aula cancelada não conta). Igual ao do init.js e do Atlas — se
     * mudar aqui, mude lá também, senão o servidor não sobe por conflito de índice.
     */
    public static void garantirIndices() {
        colecao().createIndex(
                Indexes.ascending("professorId", "dataHora"),
                new IndexOptions()
                        .name("professor_horario_unico")
                        .unique(true)
                        .partialFilterExpression(Filters.in("status", List.of(AGENDADA, REALIZADA))));
    }

    /**
     * Marca a aula do aluno no horário livre. Se o horário já tem aula, o índice
     * único faz o insert lançar MongoWriteException com DUPLICATE_KEY.
     */
    public static Document agendar(Document horario, ObjectId alunoId) {
        Document aula = new Document()
                .append("professorId", horario.getObjectId("professorId"))
                .append("alunoId", alunoId)
                .append("dataHora", Horarios.inicio(horario))
                .append("status", AGENDADA)
                .append("modalidade", horario.getString("modalidade"));
        colecao().insertOne(aula);
        return aula;
    }

    /** Versão da aula que vai para o site. */
    public static Document paraJson(Document aula, Document horario) {
        return new Document()
                .append("id", aula.getObjectId("_id").toHexString())
                .append("horarioId", horario.getObjectId("_id").toHexString())
                .append("professorId", aula.getObjectId("professorId").toHexString())
                .append("alunoId", aula.getObjectId("alunoId").toHexString())
                .append("dia", horario.getString("dia"))
                .append("horaInicio", horario.getString("horaInicio"))
                .append("horaFim", horario.getString("horaFim"))
                .append("modalidade", aula.getString("modalidade"))
                .append("status", aula.getString("status"));
    }
}
