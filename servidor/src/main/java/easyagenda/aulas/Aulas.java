package easyagenda.aulas;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.Sorts;

import easyagenda.Database;
import easyagenda.auth.Auth;
import easyagenda.auth.Usuarios;
import easyagenda.horarios.Horarios;

/**
 * Tudo que mexe na coleção "aulas" fica aqui.
 */
public final class Aulas {

    public static final String AGENDADA = "agendada";
    public static final String REALIZADA = "realizada";
    public static final String CANCELADA = "cancelada";

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

    /** Aulas agendadas que ainda não começaram, da mais próxima para a mais distante. */
    public static List<Document> proximas(Auth.Usuario usuario) {
        List<Document> lista = new ArrayList<>();
        colecao().find(Filters.and(
                        doUsuario(usuario),
                        Filters.eq("status", AGENDADA),
                        Filters.gt("dataHora", new Date())))
                .sort(Sorts.ascending("dataHora"))
                .into(lista);
        return lista;
    }

    /** Aulas que já começaram ou foram canceladas, da mais recente para a mais antiga. */
    public static List<Document> historico(Auth.Usuario usuario) {
        List<Document> lista = new ArrayList<>();
        colecao().find(Filters.and(
                        doUsuario(usuario),
                        Filters.or(
                                Filters.lte("dataHora", new Date()),
                                Filters.eq("status", CANCELADA))))
                .sort(Sorts.descending("dataHora"))
                .into(lista);
        return lista;
    }

    /** O professor vê as aulas que dá; o aluno, as aulas em que é o aluno. */
    private static Bson doUsuario(Auth.Usuario usuario) {
        String campo = Usuarios.PROFESSOR.equals(usuario.tipo()) ? "professorId" : "alunoId";
        return Filters.eq(campo, new ObjectId(usuario.id()));
    }

    /** Horário livre de onde a aula saiu, ou null se o professor já o removeu. */
    public static Document horarioDaAula(Document aula) {
        LocalDateTime quando = LocalDateTime.ofInstant(aula.getDate("dataHora").toInstant(), Horarios.FUSO);
        return Horarios.colecao().find(Filters.and(
                Filters.eq("professorId", aula.getObjectId("professorId")),
                Filters.eq("dia", quando.toLocalDate().toString()),
                Filters.eq("horaInicio", quando.toLocalTime().toString()))).first();
    }

    /** Versão da aula que vai para o site. O horário pode ser null (horaFim fica null). */
    public static Document paraJson(Document aula, Document horario) {
        LocalDateTime quando = LocalDateTime.ofInstant(aula.getDate("dataHora").toInstant(), Horarios.FUSO);
        return new Document()
                .append("id", aula.getObjectId("_id").toHexString())
                .append("horarioId", horario == null ? null : horario.getObjectId("_id").toHexString())
                .append("professorId", aula.getObjectId("professorId").toHexString())
                .append("alunoId", aula.getObjectId("alunoId").toHexString())
                .append("dia", quando.toLocalDate().toString())
                .append("horaInicio", quando.toLocalTime().toString())
                .append("horaFim", horario == null ? null : horario.getString("horaFim"))
                .append("modalidade", aula.getString("modalidade"))
                .append("status", aula.getString("status"));
    }
}
