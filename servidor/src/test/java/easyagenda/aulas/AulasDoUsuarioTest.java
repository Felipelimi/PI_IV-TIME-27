package easyagenda.aulas;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;

import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import easyagenda.Database;
import easyagenda.Server;
import easyagenda.auth.Usuarios;
import easyagenda.horarios.Horarios;

/**
 * Testes de próximas aulas e histórico (tarefa 4.3).
 *
 * Precisa do Mongo no ar: docker compose up -d mongo
 * Usa o banco "easyagenda_test", que é limpo antes e depois.
 */
class AulasDoUsuarioTest {

    private static final String BANCO_DE_TESTE = "easyagenda_test";

    // Hora cheia, para as datas gravadas baterem com as que voltam da API
    private static final Instant AGORA = Instant.now().truncatedTo(ChronoUnit.HOURS);

    private static Server server;
    private static String base;
    private static final HttpClient client = HttpClient.newHttpClient();

    private static String tokenAna;
    private static String tokenBruno;
    private static String tokenMarina;
    private static String tokenPedro;
    private static ObjectId ana;
    private static ObjectId bruno;
    private static ObjectId marina;
    private static ObjectId pedro;

    @BeforeAll
    static void subirTudo() throws Exception {
        String uri = System.getenv().getOrDefault("MONGODB_URI", "mongodb://localhost:27017");
        try {
            Database.connect(uri, BANCO_DE_TESTE);
        } catch (RuntimeException e) {
            fail("Não consegui conectar no MongoDB (" + uri + "). Suba o banco com: docker compose up -d mongo");
        }
        Usuarios.colecao().drop();
        Usuarios.garantirIndices();

        server = new Server(0);
        server.start();
        base = "http://localhost:" + server.getPort();

        tokenAna = cadastrarEEntrar("Ana", "ana.aulas@teste.com", "professor");
        tokenBruno = cadastrarEEntrar("Bruno", "bruno.aulas@teste.com", "professor");
        tokenMarina = cadastrarEEntrar("Marina", "marina.aulas@teste.com", "aluno");
        tokenPedro = cadastrarEEntrar("Pedro", "pedro.aulas@teste.com", "aluno");
        ana = idDe("ana.aulas@teste.com");
        bruno = idDe("bruno.aulas@teste.com");
        marina = idDe("marina.aulas@teste.com");
        pedro = idDe("pedro.aulas@teste.com");
    }

    @BeforeEach
    void limparAgenda() {
        Horarios.colecao().drop();
        Aulas.colecao().drop();
        Aulas.garantirIndices(); // o drop leva o índice único junto
    }

    @AfterAll
    static void derrubarTudo() {
        Usuarios.colecao().drop();
        Horarios.colecao().drop();
        Aulas.colecao().drop();
        server.stop();
    }

    @Test
    @DisplayName("aluno vê só as suas aulas: próximas em ordem crescente, histórico em ordem decrescente")
    void aulasDoAluno() throws Exception {
        ObjectId amanha = aula(ana, marina, horas(24), "agendada");
        ObjectId depoisDeAmanha = aula(ana, marina, horas(48), "agendada");
        ObjectId comBruno = aula(bruno, marina, horas(120), "agendada");
        ObjectId canceladaFutura = aula(ana, marina, horas(72), "cancelada");
        ObjectId ontem = aula(ana, marina, horas(-24), "realizada");
        ObjectId anteontem = aula(ana, marina, horas(-48), "agendada"); // passou sem ninguém mudar o status
        aula(ana, pedro, horas(25), "agendada"); // de outro aluno: não pode aparecer

        assertEquals(List.of(amanha, depoisDeAmanha, comBruno), ids("/aulas/proximas", tokenMarina));
        assertEquals(List.of(canceladaFutura, ontem, anteontem), ids("/aulas/historico", tokenMarina));
    }

    @Test
    @DisplayName("professor vê só as aulas que dá, de todos os alunos")
    void aulasDoProfessor() throws Exception {
        ObjectId marinaAmanha = aula(ana, marina, horas(24), "agendada");
        ObjectId pedroAmanha = aula(ana, pedro, horas(26), "agendada");
        ObjectId canceladaFutura = aula(ana, pedro, horas(30), "cancelada");
        ObjectId ontem = aula(ana, marina, horas(-24), "realizada");
        aula(bruno, marina, horas(10), "agendada");  // de outro professor
        aula(bruno, pedro, horas(-10), "realizada"); // de outro professor

        assertEquals(List.of(marinaAmanha, pedroAmanha), ids("/aulas/proximas", tokenAna));
        assertEquals(List.of(canceladaFutura, ontem), ids("/aulas/historico", tokenAna));
    }

    @Test
    @DisplayName("quem não tem aulas recebe listas vazias")
    void semAulas() throws Exception {
        aula(ana, marina, horas(24), "agendada");
        aula(ana, marina, horas(-24), "realizada");

        assertTrue(ids("/aulas/proximas", tokenPedro).isEmpty());
        assertTrue(ids("/aulas/historico", tokenPedro).isEmpty());
        assertTrue(ids("/aulas/proximas", tokenBruno).isEmpty());
        assertTrue(ids("/aulas/historico", tokenBruno).isEmpty());
    }

    @Test
    @DisplayName("aula agendada pelo site aparece nas próximas com dia, horário e nomes")
    void aulaAgendadaAparece() throws Exception {
        String dia = LocalDate.now(Horarios.FUSO).plusDays(7).toString();
        HttpResponse<String> horario = enviar("POST", "/horarios",
                "{\"dia\":\"" + dia + "\",\"horaInicio\":\"14:00\",\"horaFim\":\"15:00\",\"modalidade\":\"online\"}", tokenAna);
        String horarioId = Document.parse(horario.body()).getString("id");
        assertEquals(201, enviar("POST", "/aulas", "{\"horarioId\":\"" + horarioId + "\"}", tokenMarina).statusCode());

        List<Document> proximas = aulas("/aulas/proximas", tokenMarina);
        assertEquals(1, proximas.size());
        Document aula = proximas.get(0);
        assertEquals(dia, aula.getString("dia"));
        assertEquals("14:00", aula.getString("horaInicio"));
        assertEquals("15:00", aula.getString("horaFim"));
        assertEquals(horarioId, aula.getString("horarioId"));
        assertEquals("Ana", aula.getString("professorNome"));
        assertEquals("Marina", aula.getString("alunoNome"));
        assertEquals("agendada", aula.getString("status"));

        // A professora vê a mesma aula
        assertEquals(1, aulas("/aulas/proximas", tokenAna).size());
    }

    @Test
    @DisplayName("sem token não entra")
    void semToken() throws Exception {
        assertEquals(401, enviar("GET", "/aulas/proximas", null, null).statusCode());
        assertEquals(401, enviar("GET", "/aulas/historico", null, null).statusCode());
    }

    // ----- ajudantes -----

    private static Date horas(long quantas) {
        return Date.from(AGORA.plus(Duration.ofHours(quantas)));
    }

    /** Grava uma aula direto no banco (a API não deixa criar aula no passado). */
    private static ObjectId aula(ObjectId professor, ObjectId aluno, Date quando, String status) {
        Document aula = new Document()
                .append("professorId", professor)
                .append("alunoId", aluno)
                .append("dataHora", quando)
                .append("status", status)
                .append("modalidade", "online");
        Aulas.colecao().insertOne(aula);
        return aula.getObjectId("_id");
    }

    private static List<Document> aulas(String caminho, String token) throws Exception {
        HttpResponse<String> resposta = enviar("GET", caminho, null, token);
        assertEquals(200, resposta.statusCode(), resposta.body());
        return Document.parse(resposta.body()).getList("aulas", Document.class);
    }

    /** Ids das aulas na ordem em que a rota devolveu. */
    private static List<ObjectId> ids(String caminho, String token) throws Exception {
        return aulas(caminho, token).stream().map(a -> new ObjectId(a.getString("id"))).toList();
    }

    private static ObjectId idDe(String email) {
        return Usuarios.buscarPorEmail(email).getObjectId("_id");
    }

    private static HttpResponse<String> enviar(String metodo, String caminho, String json, String token) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(base + caminho))
                .header("Content-Type", "application/json")
                .method(metodo, json == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(json));
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static String cadastrarEEntrar(String nome, String email, String tipo) throws Exception {
        HttpResponse<String> cadastro = enviar("POST", "/cadastro",
                "{\"nome\":\"" + nome + "\",\"email\":\"" + email + "\",\"senha\":\"senha123\",\"tipo\":\"" + tipo + "\"}", null);
        assertTrue(cadastro.statusCode() == 201, "cadastro falhou: " + cadastro.body());
        HttpResponse<String> login = enviar("POST", "/login",
                "{\"email\":\"" + email + "\",\"senha\":\"senha123\"}", null);
        return Document.parse(login.body()).getString("token");
    }
}
