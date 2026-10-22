package easyagenda.horarios;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.util.List;

import org.bson.Document;
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
import easyagenda.Server;
import easyagenda.auth.Usuarios;

/**
 * Testes dos horários do professor (tarefa 2.2).
 *
 * Precisa do Mongo no ar: docker compose up -d mongo
 * Usa o banco "easyagenda_test", que é limpo antes e depois.
 */
class HorariosTest {

    private static final String BANCO_DE_TESTE = "easyagenda_test";

    // Sempre um dia no futuro, para o teste não depender da data de hoje
    private static final String DIA = LocalDate.now(Horarios.FUSO).plusDays(7).toString();

    private static Server server;
    private static String base;
    private static final HttpClient client = HttpClient.newHttpClient();

    private static String tokenAna;
    private static String tokenCarlos;
    private static String tokenAluno;
    private static String idAna;

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

        tokenAna = cadastrarEEntrar("Ana", "ana.horarios@teste.com", "professor");
        tokenCarlos = cadastrarEEntrar("Carlos", "carlos.horarios@teste.com", "professor");
        tokenAluno = cadastrarEEntrar("Marina", "marina.horarios@teste.com", "aluno");
        idAna = Usuarios.buscarPorEmail("ana.horarios@teste.com").getObjectId("_id").toHexString();
    }

    @BeforeEach
    void limparHorarios() {
        Horarios.colecao().drop();
        Database.get().getCollection("aulas").drop();
    }

    @AfterAll
    static void derrubarTudo() {
        Usuarios.colecao().drop();
        Horarios.colecao().drop();
        Database.get().getCollection("aulas").drop();
        server.stop();
    }

    @Test
    @DisplayName("professor cria um horário livre e ele aparece na lista dele")
    void criarHorario() throws Exception {
        HttpResponse<String> resposta = enviar("POST", "/horarios", horario("14:00", "15:00", "online"), tokenAna);

        assertEquals(201, resposta.statusCode());
        Document criado = Document.parse(resposta.body());
        assertEquals(DIA, criado.getString("dia"));
        assertEquals("online", criado.getString("modalidade"));

        HttpResponse<String> lista = enviar("GET", "/horarios?de=" + DIA + "&ate=" + DIA, null, tokenAna);
        assertEquals(200, lista.statusCode());
        assertEquals(1, Document.parse(lista.body()).getList("horarios", Document.class).size());
    }

    @Test
    @DisplayName("horário que encosta em outro do mesmo professor devolve 409")
    void horarioSobreposto() throws Exception {
        assertEquals(201, enviar("POST", "/horarios", horario("14:00", "15:00", "online"), tokenAna).statusCode());

        HttpResponse<String> sobreposto = enviar("POST", "/horarios", horario("14:30", "15:30", "presencial"), tokenAna);
        assertEquals(409, sobreposto.statusCode());

        // Outro professor pode ter o mesmo horário
        assertEquals(201, enviar("POST", "/horarios", horario("14:00", "15:00", "online"), tokenCarlos).statusCode());
    }

    @Test
    @DisplayName("outro professor não consegue mudar nem remover o horário")
    void soODonoMexe() throws Exception {
        String id = Document.parse(enviar("POST", "/horarios", horario("10:00", "11:00", "online"), tokenAna).body())
                .getString("id");

        assertEquals(403, enviar("PUT", "/horarios/" + id, "{\"modalidade\":\"presencial\"}", tokenCarlos).statusCode());
        assertEquals(403, enviar("DELETE", "/horarios/" + id, null, tokenCarlos).statusCode());

        // O dono consegue
        assertEquals(200, enviar("PUT", "/horarios/" + id, "{\"modalidade\":\"presencial\"}", tokenAna).statusCode());
        assertEquals(204, enviar("DELETE", "/horarios/" + id, null, tokenAna).statusCode());
    }

    @Test
    @DisplayName("aluno não cria horário e sem token não entra")
    void soProfessorCria() throws Exception {
        assertEquals(403, enviar("POST", "/horarios", horario("10:00", "11:00", "online"), tokenAluno).statusCode());
        assertEquals(401, enviar("POST", "/horarios", horario("10:00", "11:00", "online"), null).statusCode());
    }

    @Test
    @DisplayName("o link do professor mostra só os horários livres, sem precisar de login")
    void linkDoProfessor() throws Exception {
        enviar("POST", "/horarios", horario("08:00", "09:00", "online"), tokenAna);
        String ocupado = Document.parse(enviar("POST", "/horarios", horario("09:00", "10:00", "presencial"), tokenAna).body())
                .getString("id");

        // Simula uma aula já agendada no horário das 09:00
        Document h = Horarios.buscarPorId(new org.bson.types.ObjectId(ocupado));
        Database.get().getCollection("aulas").insertOne(new Document()
                .append("professorId", h.getObjectId("professorId"))
                .append("alunoId", Usuarios.buscarPorEmail("marina.horarios@teste.com").getObjectId("_id"))
                .append("dataHora", Horarios.inicio(h))
                .append("status", "agendada")
                .append("modalidade", "presencial"));

        HttpResponse<String> resposta = enviar("GET", "/professores/" + idAna + "/horarios?de=" + DIA + "&ate=" + DIA, null, null);
        assertEquals(200, resposta.statusCode());

        Document corpo = Document.parse(resposta.body());
        assertEquals("Ana", corpo.get("professor", Document.class).getString("nome"));
        List<Document> livres = corpo.getList("horarios", Document.class);
        assertEquals(1, livres.size());
        assertEquals("08:00", livres.get(0).getString("horaInicio"));

        // O horário com aluno não pode ser removido
        assertEquals(409, enviar("DELETE", "/horarios/" + ocupado, null, tokenAna).statusCode());
    }

    @Test
    @DisplayName("dados inválidos devolvem 400")
    void dadosInvalidos() throws Exception {
        assertEquals(400, enviar("POST", "/horarios", horario("15:00", "14:00", "online"), tokenAna).statusCode());
        assertEquals(400, enviar("POST", "/horarios", horario("14:00", "15:00", "telepatia"), tokenAna).statusCode());
        assertEquals(400, enviar("POST", "/horarios",
                "{\"dia\":\"2020-01-01\",\"horaInicio\":\"14:00\",\"horaFim\":\"15:00\",\"modalidade\":\"online\"}", tokenAna).statusCode());
        assertFalse(Horarios.colecao().countDocuments() > 0);
    }

    // ----- ajudantes -----

    private static String horario(String inicio, String fim, String modalidade) {
        return "{\"dia\":\"" + DIA + "\",\"horaInicio\":\"" + inicio + "\",\"horaFim\":\"" + fim
                + "\",\"modalidade\":\"" + modalidade + "\"}";
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
