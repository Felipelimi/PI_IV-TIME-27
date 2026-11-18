package easyagenda.lembretes;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import easyagenda.Database;
import easyagenda.aulas.Aulas;
import easyagenda.auth.Usuarios;

/**
 * Testes do lembrete por e-mail (tarefa 3.3). Não manda e-mail de verdade:
 * usa um carteiro falso que só anota o que seria enviado.
 *
 * Precisa do Mongo no ar: docker compose up -d mongo
 * Usa o banco "easyagenda_test", que é limpo antes e depois.
 */
class LembretesTest {

    private static final String BANCO_DE_TESTE = "easyagenda_test";

    // Relógio fixo: 18/11/2026 12:00 UTC = 09:00 em São Paulo
    private static final Instant AGORA = Instant.parse("2026-11-18T12:00:00Z");

    private static ObjectId ana;     // professora
    private static ObjectId marina;  // aluna

    private CarteiroFalso carteiro;
    private Lembretes lembretes;

    @BeforeAll
    static void conectar() {
        String uri = System.getenv().getOrDefault("MONGODB_URI", "mongodb://localhost:27017");
        try {
            Database.connect(uri, BANCO_DE_TESTE);
        } catch (RuntimeException e) {
            fail("Não consegui conectar no MongoDB (" + uri + "). Suba o banco com: docker compose up -d mongo");
        }
        Usuarios.colecao().drop();
        Usuarios.garantirIndices();
        ana = Usuarios.criar("Ana", "ana.lembrete@teste.com", "senha123", Usuarios.PROFESSOR).getObjectId("_id");
        marina = Usuarios.criar("Marina", "marina.lembrete@teste.com", "senha123", Usuarios.ALUNO).getObjectId("_id");
    }

    @BeforeEach
    void limpar() {
        Aulas.colecao().drop();
        Aulas.garantirIndices(); // o drop leva o índice único junto
        carteiro = new CarteiroFalso();
        lembretes = new Lembretes(carteiro, Lembretes.ANTECEDENCIA_PADRAO);
    }

    @AfterAll
    static void limparTudo() {
        Usuarios.colecao().drop();
        Aulas.colecao().drop();
    }

    @Test
    @DisplayName("aula de amanhã gera um e-mail para o aluno e outro para o professor")
    void aulaDeAmanhaGeraEmail() {
        ObjectId aula = aula(horas(20), Aulas.AGENDADA); // 19/11 08:00 UTC = 05:00 em São Paulo

        assertEquals(1, lembretes.enviarPendentes(AGORA));

        assertEquals(Set.of("marina.lembrete@teste.com", "ana.lembrete@teste.com"), carteiro.destinatarios());
        assertEquals(2, carteiro.enviados.size());
        for (Enviado e : carteiro.enviados) {
            assertEquals("Lembrete: aula em 19/11/2026 às 05:00", e.assunto());
            assertTrue(e.texto().contains("Horário: 05:00"), e.texto());
            assertTrue(e.texto().contains("Modalidade: online"), e.texto());
        }
        assertTrue(textoPara("marina.lembrete@teste.com").contains("Olá, Marina!"));
        assertTrue(textoPara("marina.lembrete@teste.com").contains("com o professor Ana"));
        assertTrue(textoPara("ana.lembrete@teste.com").contains("com o aluno Marina"));

        Document salva = Aulas.colecao().find(new Document("_id", aula)).first();
        assertEquals(Boolean.TRUE, salva.getBoolean("lembreteEnviado"));
        assertEquals(Date.from(AGORA), salva.getDate("lembreteEnviadoEm"));
    }

    @Test
    @DisplayName("o lembrete não é enviado duas vezes, nem em rodadas seguidas")
    void naoEnviaDuasVezes() {
        aula(horas(20), Aulas.AGENDADA);

        assertEquals(1, lembretes.enviarPendentes(AGORA));
        assertEquals(0, lembretes.enviarPendentes(AGORA.plusSeconds(600)));
        assertEquals(0, lembretes.enviarPendentes(AGORA.plusSeconds(3600)));

        assertEquals(2, carteiro.enviados.size());
    }

    @Test
    @DisplayName("só entra aula agendada que começa nas próximas 24 horas")
    void soAulasDentroDaAntecedencia() {
        ObjectId daquiTresDias = aula(horas(72), Aulas.AGENDADA);
        aula(horas(10), Aulas.CANCELADA);
        aula(horas(-2), Aulas.AGENDADA);   // já começou
        aula(horas(-30), Aulas.REALIZADA);

        assertEquals(0, lembretes.enviarPendentes(AGORA));
        assertTrue(carteiro.enviados.isEmpty());

        // Dois dias depois, a aula de "daqui a três dias" entra nas 24 horas
        assertEquals(1, lembretes.enviarPendentes(AGORA.plus(Duration.ofHours(49))));
        assertEquals(2, carteiro.enviados.size());
        assertEquals(Boolean.TRUE, Aulas.colecao().find(new Document("_id", daquiTresDias)).first()
                .getBoolean("lembreteEnviado"));
    }

    @Test
    @DisplayName("se nenhum e-mail sai, a aula não fica marcada e a próxima rodada tenta de novo")
    void falhaNoEnvioTentaDeNovo() {
        ObjectId aula = aula(horas(20), Aulas.AGENDADA);
        carteiro.falhar = true;

        assertEquals(0, lembretes.enviarPendentes(AGORA));
        assertFalse(Aulas.colecao().find(new Document("_id", aula)).first().containsKey("lembreteEnviado"));

        carteiro.falhar = false;
        assertEquals(1, lembretes.enviarPendentes(AGORA.plusSeconds(600)));
        assertEquals(2, carteiro.enviados.size());
    }

    @Test
    @DisplayName("duas rodadas ao mesmo tempo não repetem o lembrete")
    void rodadasAoMesmoTempo() throws Exception {
        for (int i = 1; i <= 10; i++) {
            aula(horas(i), Aulas.AGENDADA);
        }

        ExecutorService threads = Executors.newFixedThreadPool(4);
        CountDownLatch largada = new CountDownLatch(1);
        List<Future<Integer>> rodadas = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            rodadas.add(threads.submit(() -> {
                largada.await();
                return lembretes.enviarPendentes(AGORA);
            }));
        }
        largada.countDown();
        int total = 0;
        for (Future<Integer> rodada : rodadas) {
            total += rodada.get();
        }
        threads.shutdown();

        assertEquals(10, total);
        assertEquals(20, carteiro.enviados.size());
    }

    // ----- ajudantes -----

    private static Instant horas(long h) {
        return AGORA.plus(Duration.ofHours(h));
    }

    private static ObjectId aula(Instant quando, String status) {
        Document aula = new Document()
                .append("professorId", ana)
                .append("alunoId", marina)
                .append("dataHora", Date.from(quando))
                .append("status", status)
                .append("modalidade", "online");
        Aulas.colecao().insertOne(aula);
        return aula.getObjectId("_id");
    }

    private String textoPara(String email) {
        return carteiro.enviados.stream()
                .filter(e -> e.para().equals(email))
                .findFirst()
                .orElseThrow()
                .texto();
    }

    private record Enviado(String para, String assunto, String texto) {
    }

    /** Carteiro que não manda nada: só guarda os e-mails (ou falha, se pedirmos). */
    private static final class CarteiroFalso implements EnvioDeEmail {
        final List<Enviado> enviados = Collections.synchronizedList(new ArrayList<>());
        volatile boolean falhar;

        @Override
        public void enviar(String para, String assunto, String texto) throws Exception {
            if (falhar) {
                throw new Exception("SMTP fora do ar");
            }
            enviados.add(new Enviado(para, assunto, texto));
        }

        Set<String> destinatarios() {
            Set<String> emails = ConcurrentHashMap.newKeySet();
            synchronized (enviados) {
                enviados.forEach(e -> emails.add(e.para()));
            }
            return emails;
        }
    }
}
