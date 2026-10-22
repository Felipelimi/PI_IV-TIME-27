package easyagenda.horarios;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.bson.Document;
import org.bson.types.ObjectId;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.Updates;

import easyagenda.Database;

/**
 * Tudo que mexe na coleção "horarios_livres" fica aqui.
 * dia no formato 2026-10-20 e horas no formato 14:00.
 */
public final class Horarios {

    public static final String ONLINE = "online";
    public static final String PRESENCIAL = "presencial";

    /** Fuso usado para saber se um horário já passou e para gravar a data das aulas. */
    public static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");

    private Horarios() {
    }

    public static MongoCollection<Document> colecao() {
        return Database.get().getCollection("horarios_livres");
    }

    /** Horários de um professor entre os dias de e ate (inclusive), em ordem. */
    public static List<Document> listarDoProfessor(ObjectId professorId, String de, String ate) {
        List<Document> lista = new ArrayList<>();
        colecao().find(Filters.and(
                        Filters.eq("professorId", professorId),
                        Filters.gte("dia", de),
                        Filters.lte("dia", ate)))
                .sort(Sorts.ascending("dia", "horaInicio"))
                .into(lista);
        return lista;
    }

    public static Document buscarPorId(ObjectId id) {
        return colecao().find(Filters.eq("_id", id)).first();
    }

    /** True se o professor já tem um horário que encosta no intervalo [inicio, fim) desse dia. */
    public static boolean temSobreposicao(ObjectId professorId, String dia, String inicio, String fim) {
        for (Document h : listarDoProfessor(professorId, dia, dia)) {
            boolean sobrepoe = inicio.compareTo(h.getString("horaFim")) < 0
                    && h.getString("horaInicio").compareTo(fim) < 0;
            if (sobrepoe) {
                return true;
            }
        }
        return false;
    }

    public static Document criar(ObjectId professorId, String dia, String inicio, String fim, String modalidade) {
        Document horario = new Document()
                .append("professorId", professorId)
                .append("dia", dia)
                .append("horaInicio", inicio)
                .append("horaFim", fim)
                .append("modalidade", modalidade);
        colecao().insertOne(horario);
        return horario;
    }

    public static void mudarModalidade(ObjectId id, String modalidade) {
        colecao().updateOne(Filters.eq("_id", id), Updates.set("modalidade", modalidade));
    }

    public static void remover(ObjectId id) {
        colecao().deleteOne(Filters.eq("_id", id));
    }

    /** Data e hora de início do horário, no fuso de São Paulo. */
    public static Date inicio(Document horario) {
        LocalDateTime quando = LocalDateTime.of(
                LocalDate.parse(horario.getString("dia")),
                LocalTime.parse(horario.getString("horaInicio")));
        return Date.from(quando.atZone(FUSO).toInstant());
    }

    /** True se já tem aluno nesse horário (aula agendada ou realizada na coleção "aulas"). */
    public static boolean estaReservado(Document horario) {
        long aulas = Database.get().getCollection("aulas").countDocuments(Filters.and(
                Filters.eq("professorId", horario.getObjectId("professorId")),
                Filters.eq("dataHora", inicio(horario)),
                Filters.in("status", "agendada", "realizada")));
        return aulas > 0;
    }

    /** True se o horário já começou ou passou. */
    public static boolean jaPassou(Document horario) {
        return !inicio(horario).after(new Date());
    }

    /** Versão do horário que vai para o site. */
    public static Document paraJson(Document horario) {
        return new Document()
                .append("id", horario.getObjectId("_id").toHexString())
                .append("professorId", horario.getObjectId("professorId").toHexString())
                .append("dia", horario.getString("dia"))
                .append("horaInicio", horario.getString("horaInicio"))
                .append("horaFim", horario.getString("horaFim"))
                .append("modalidade", horario.getString("modalidade"));
    }
}
