package easyagenda.lembretes;

import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;

import easyagenda.aulas.Aulas;
import easyagenda.auth.Usuarios;
import easyagenda.horarios.Horarios;

/**
 * Lembrete por e-mail das aulas (tarefa 3.3).
 *
 * De tempos em tempos procura as aulas agendadas que começam dentro da
 * antecedência (24 horas por padrão) e manda um e-mail para o aluno e outro
 * para o professor. A aula fica marcada com lembreteEnviado = true para não
 * receber o lembrete de novo.
 */
public final class Lembretes {

    public static final Duration ANTECEDENCIA_PADRAO = Duration.ofHours(24);

    private static final Locale PT_BR = Locale.of("pt", "BR");
    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy", PT_BR);
    private static final DateTimeFormatter DIA_DA_SEMANA = DateTimeFormatter.ofPattern("EEEE", PT_BR);
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm", PT_BR);

    private final EnvioDeEmail email;
    private final Duration antecedencia;

    public Lembretes(EnvioDeEmail email, Duration antecedencia) {
        this.email = email;
        this.antecedencia = antecedencia;
    }

    /**
     * Roda a rotina a cada intervalo, começando agora. Erro numa rodada não
     * para as próximas.
     */
    public ScheduledExecutorService iniciar(Duration intervalo) {
        ScheduledExecutorService agendador = Executors.newSingleThreadScheduledExecutor(tarefa -> {
            Thread thread = new Thread(tarefa, "lembretes");
            thread.setDaemon(true);
            return thread;
        });
        agendador.scheduleWithFixedDelay(() -> {
            try {
                int enviados = enviarPendentes(Instant.now());
                if (enviados > 0) {
                    System.out.println("Lembretes enviados: " + enviados + " aula(s).");
                }
            } catch (RuntimeException e) {
                System.err.println("Falha na rotina de lembretes: " + e.getMessage());
                e.printStackTrace();
            }
        }, 0, intervalo.toSeconds(), TimeUnit.SECONDS);
        return agendador;
    }

    /**
     * Uma rodada: envia o lembrete das aulas agendadas que começam entre agora
     * e agora + antecedência e que ainda não receberam lembrete.
     *
     * @return quantas aulas tiveram o lembrete enviado nesta rodada.
     */
    public int enviarPendentes(Instant agora) {
        Bson pendente = Filters.and(
                Filters.eq("status", Aulas.AGENDADA),
                Filters.gt("dataHora", Date.from(agora)),
                Filters.lte("dataHora", Date.from(agora.plus(antecedencia))),
                Filters.ne("lembreteEnviado", true));

        List<Document> aulas = new ArrayList<>();
        Aulas.colecao().find(pendente).into(aulas);

        int enviados = 0;
        for (Document aula : aulas) {
            if (reservar(aula, agora) && enviar(aula)) {
                enviados++;
            }
        }
        return enviados;
    }

    /**
     * Marca a aula como "lembrete enviado" ANTES de enviar. O update só pega a
     * aula se ela ainda não estiver marcada, então, se duas rodadas (ou dois
     * servidores) pegarem a mesma aula ao mesmo tempo, só uma ganha.
     */
    private boolean reservar(Document aula, Instant agora) {
        Document reservada = Aulas.colecao().findOneAndUpdate(
                Filters.and(
                        Filters.eq("_id", aula.getObjectId("_id")),
                        Filters.ne("lembreteEnviado", true)),
                Updates.combine(
                        Updates.set("lembreteEnviado", true),
                        Updates.set("lembreteEnviadoEm", Date.from(agora))));
        return reservada != null;
    }

    /**
     * Envia para o aluno e para o professor. Se nenhum e-mail saiu, desfaz a
     * marcação para tentar de novo na próxima rodada. Se um saiu e o outro não,
     * a marcação fica: preferimos não mandar o lembrete repetido para quem já
     * recebeu.
     */
    private boolean enviar(Document aula) {
        Document aluno = Usuarios.buscarPorId(aula.getObjectId("alunoId"));
        Document professor = Usuarios.buscarPorId(aula.getObjectId("professorId"));

        boolean algumSaiu = false;
        if (aluno != null) {
            algumSaiu |= tentar(aluno, professor, aula, "com o professor");
        }
        if (professor != null) {
            algumSaiu |= tentar(professor, aluno, aula, "com o aluno");
        }
        if (!algumSaiu && (aluno != null || professor != null)) {
            desfazerReserva(aula.getObjectId("_id"));
        }
        return algumSaiu;
    }

    private boolean tentar(Document destinatario, Document outro, Document aula, String comQuem) {
        String para = destinatario.getString("email");
        try {
            email.enviar(para, assunto(aula), texto(destinatario, outro, aula, comQuem));
            return true;
        } catch (Exception e) {
            System.err.println("Não consegui enviar o lembrete para " + para + ": " + e.getMessage());
            return false;
        }
    }

    private void desfazerReserva(ObjectId aulaId) {
        Aulas.colecao().updateOne(
                Filters.eq("_id", aulaId),
                Updates.combine(Updates.unset("lembreteEnviado"), Updates.unset("lembreteEnviadoEm")));
    }

    static String assunto(Document aula) {
        ZonedDateTime quando = quando(aula);
        return "Lembrete: aula em " + quando.format(DATA) + " às " + quando.format(HORA);
    }

    static String texto(Document destinatario, Document outro, Document aula, String comQuem) {
        ZonedDateTime quando = quando(aula);
        String nomeDoOutro = outro == null ? "" : " " + comQuem + " " + outro.getString("nome");
        return """
                Olá, %s!

                Este é um lembrete da sua aula%s.

                Data: %s (%s)
                Horário: %s
                Modalidade: %s

                Até lá!
                EasyAgenda
                """.formatted(
                destinatario.getString("nome"),
                nomeDoOutro,
                quando.format(DATA),
                quando.format(DIA_DA_SEMANA),
                quando.format(HORA),
                aula.getString("modalidade"));
    }

    private static ZonedDateTime quando(Document aula) {
        return aula.getDate("dataHora").toInstant().atZone(Horarios.FUSO);
    }
}
