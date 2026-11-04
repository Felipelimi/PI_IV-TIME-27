package easyagenda.aulas;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.bson.Document;
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
 * Testes do agendamento de aula pelo aluno (tarefa 3.1).
 *
 * Precisa do Mongo no ar: docker compose up -d mongo
 * Usa o banco "easyagenda_test", que é limpo antes e depois.
 */
class AgendamentoTest {

    private static final String BANCO_DE_TESTE = "easyagenda_test";

    // Sempre um dia no futuro, para o teste não depender da data de hoje
    private static final String DIA = LocalDate.now(Horarios.FUSO).plusDays(7).toString();

    private static Server server;
    private static String base;
    private static final HttpClient client = HttpClient.newHttpClient();

    private static String tokenProfessor;
    private static String tokenMarina;
    private static String tokenPedro;
    private static String idProfessor;

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

        tokenProfessor = cadastrarEEntrar("Ana", "ana.agendamento@teste.com", "professor");
        tokenMarina = cadastrarEEntrar("Marina", "marina.agendamento@teste.com", "aluno");
        tokenPedro = cadastrarEEntrar("Pedro", "pedro.agendamento@teste.com", "aluno");
        idProfessor = Usuarios.buscarPorEmail("ana.agendamento@teste.com").getObjectId("_id").toHexString();
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
    @DisplayName("lista os horários livres do professor")
    void listarHorarios() throws Exception {
        criarHorario("08:00", "09:00");
        criarHorario("09:00", "10:00");

        List<Document> livres = horariosLivres();
        assertEquals(2, livres.size());
        assertEquals("08:00", livres.get(0).getString("horaInicio"));
        assertEquals("09:00", livres.get(1).getString("horaInicio"));
    }

    @Test
    @DisplayName("aluno agenda e o horário some da lista dos outros alunos")
    void agendar() throws Exception {
        criarHorario("08:00", "09:00");
        String horarioId = criarHorario("09:00", "10:00");

        HttpResponse<String> resposta = agendar(horarioId, tokenMarina);

        assertEquals(201, resposta.statusCode());
        Document aula = Document.parse(resposta.body());
        assertEquals("agendada", aula.getString("status"));
        assertEquals(DIA, aula.getString("dia"));
        assertEquals("09:00", aula.getString("horaInicio"));
        assertEquals(idProfessor, aula.getString("professorId"));

        List<Document> livres = horariosLivres();
        assertEquals(1, livres.size());
        assertEquals("08:00", livres.get(0).getString("horaInicio"));
    }

    @Test
    @DisplayName("dois alunos tentam o mesmo horário ao mesmo tempo: só o primeiro consegue")
    void doisAlunosMesmoHorario() throws Exception {
        String horarioId = criarHorario("14:00", "15:00");

        CompletableFuture<HttpResponse<String>> marina = agendarAsync(horarioId, tokenMarina);
        CompletableFuture<HttpResponse<String>> pedro = agendarAsync(horarioId, tokenPedro);
        List<HttpResponse<String>> respostas = List.of(marina.join(), pedro.join());

        long sucessos = respostas.stream().filter(r -> r.statusCode() == 201).count();
        assertEquals(1, sucessos, "exatamente um aluno deveria conseguir");
        HttpResponse<String> recusada = respostas.stream().filter(r -> r.statusCode() != 201).findFirst().orElseThrow();
        assertEquals(409, recusada.statusCode());
        assertEquals("horário indisponível", Document.parse(recusada.body()).getString("erro"));

        assertEquals(1, Aulas.colecao().countDocuments());
        assertTrue(horariosLivres().isEmpty());

        // Quem chegar depois também recebe a mesma mensagem
        HttpResponse<String> atrasado = agendar(horarioId, tokenMarina);
        assertEquals(409, atrasado.statusCode());
        assertEquals("horário indisponível", Document.parse(atrasado.body()).getString("erro"));
    }

    @Test
    @DisplayName("só aluno logado agenda, e horário inexistente devolve 404")
    void regras() throws Exception {
        String horarioId = criarHorario("10:00", "11:00");

        assertEquals(401, agendar(horarioId, null).statusCode());
        assertEquals(403, agendar(horarioId, tokenProfessor).statusCode());
        assertEquals(404, agendar("000000000000000000000000", tokenMarina).statusCode());
        assertEquals(404, agendar("nao-e-um-id", tokenMarina).statusCode());
        assertEquals(400, enviar("POST", "/aulas", "{}", tokenMarina).statusCode());
        assertEquals(0, Aulas.colecao().countDocuments());
    }

    // ----- ajudantes -----

    /** Professor cria o horário pela rota da 2.2 e devolve o id. */
    private static String criarHorario(String inicio, String fim) throws Exception {
        HttpResponse<String> resposta = enviar("POST", "/horarios",
                "{\"dia\":\"" + DIA + "\",\"horaInicio\":\"" + inicio + "\",\"horaFim\":\"" + fim
                        + "\",\"modalidade\":\"online\"}", tokenProfessor);
        assertEquals(201, resposta.statusCode(), "criar horário falhou: " + resposta.body());
        return Document.parse(resposta.body()).getString("id");
    }

    /** O que um aluno vê ao abrir o link do professor. */
    private static List<Document> horariosLivres() throws Exception {
        HttpResponse<String> resposta = enviar("GET",
                "/professores/" + idProfessor + "/horarios?de=" + DIA + "&ate=" + DIA, null, null);
        assertEquals(200, resposta.statusCode());
        return Document.parse(resposta.body()).getList("horarios", Document.class);
    }

    private static HttpResponse<String> agendar(String horarioId, String token) throws Exception {
        return enviar("POST", "/aulas", "{\"horarioId\":\"" + horarioId + "\"}", token);
    }

    private static CompletableFuture<HttpResponse<String>> agendarAsync(String horarioId, String token) {
        return client.sendAsync(requisicao("POST", "/aulas", "{\"horarioId\":\"" + horarioId + "\"}", token),
                HttpResponse.BodyHandlers.ofString());
    }

    private static HttpResponse<String> enviar(String metodo, String caminho, String json, String token) throws Exception {
        return client.send(requisicao(metodo, caminho, json, token), HttpResponse.BodyHandlers.ofString());
    }

    private static HttpRequest requisicao(String metodo, String caminho, String json, String token) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(base + caminho))
                .header("Content-Type", "application/json")
                .method(metodo, json == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(json));
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return builder.build();
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
