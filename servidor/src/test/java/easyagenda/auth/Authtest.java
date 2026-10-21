package easyagenda.auth;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.bson.Document;
import org.junit.jupiter.api.AfterAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import easyagenda.Database;
import easyagenda.Server;

/**
 * Testes de cadastro e login.
 *
 * Precisa do Mongo no ar: docker compose up -d mongo
 * Os testes usam o banco "easyagenda_test", que é limpo antes e depois.
 */
class Authtest {

    private static final String BANCO_DE_TESTE = "easyagenda_test";

    private static Server server;
    private static String base;
    private static final HttpClient client = HttpClient.newHttpClient();

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

        server = new Server(0); // porta 0 = o sistema escolhe uma porta livre
        server.start();
        base = "http://localhost:" + server.getPort();
    }

    @AfterAll
    static void derrubarTudo() {
        Usuarios.colecao().drop();
        server.stop();
    }

    // ----- cadastro -----

    @Test
    @DisplayName("cadastro com dados válidos devolve 201 e não devolve a senha")
    void cadastroOk() throws Exception {
        HttpResponse<String> resposta = post("/cadastro", """
                {"nome":"Ana Paula","email":"ana@teste.com","senha":"senha123","tipo":"professor"}
                """);

        assertEquals(201, resposta.statusCode());

        Document corpo = Document.parse(resposta.body());
        assertNotNull(corpo.getString("id"));
        assertEquals("ana@teste.com", corpo.getString("email"));
        assertEquals("professor", corpo.getString("tipo"));
        assertFalse(corpo.containsKey("senha"), "a resposta não pode devolver a senha");

        // No banco fica o hash do BCrypt, nunca a senha pura
        Document salvo = Usuarios.buscarPorEmail("ana@teste.com");
        assertNotNull(salvo);
        String hash = salvo.getString("senha");
        assertNotEqualsSenhaPura(hash);
        assertTrue(Auth.senhaConfere("senha123", hash));
    }

    @Test
    @DisplayName("cadastrar o mesmo e-mail duas vezes devolve 409")
    void emailRepetido() throws Exception {
        String json = """
                {"nome":"Carlos","email":"carlos@teste.com","senha":"senha123","tipo":"aluno"}
                """;

        assertEquals(201, post("/cadastro", json).statusCode());

        HttpResponse<String> segunda = post("/cadastro", json);
        assertEquals(409, segunda.statusCode());
        assertEquals("e-mail já cadastrado", Document.parse(segunda.body()).getString("erro"));

        assertEquals(1, Usuarios.colecao().countDocuments(new Document("email", "carlos@teste.com")));
    }

    // ----- login -----

    @Test
    @DisplayName("login com senha certa devolve token válido")
    void loginCerto() throws Exception {
        post("/cadastro", """
                {"nome":"João","email":"joao@teste.com","senha":"senha123","tipo":"aluno"}
                """);

        HttpResponse<String> resposta = post("/login", """
                {"email":"joao@teste.com","senha":"senha123"}
                """);

        assertEquals(200, resposta.statusCode());

        Document corpo = Document.parse(resposta.body());
        String token = corpo.getString("token");
        assertNotNull(token);
        assertEquals("aluno", corpo.get("usuario", Document.class).getString("tipo"));

        // O token funciona de verdade numa rota protegida
        HttpResponse<String> me = get("/me", token);
        assertEquals(200, me.statusCode());
        assertEquals("aluno", Document.parse(me.body()).getString("tipo"));
    }

    @Test
    @DisplayName("login com senha errada ou e-mail inexistente devolve 401")
    void loginErrado() throws Exception {
        post("/cadastro", """
                {"nome":"Maria","email":"maria@teste.com","senha":"senha123","tipo":"professor"}
                """);

        HttpResponse<String> senhaErrada = post("/login", """
                {"email":"maria@teste.com","senha":"senha-errada"}
                """);
        assertEquals(401, senhaErrada.statusCode());
        assertFalse(senhaErrada.body().contains("token"));

        HttpResponse<String> naoExiste = post("/login", """
                {"email":"ninguem@teste.com","senha":"senha123"}
                """);
        assertEquals(401, naoExiste.statusCode());
    }

    // ----- proteção das rotas -----

    @Test
    @DisplayName("rota protegida recusa requisição sem token ou com token inventado")
    void semTokenNaoEntra() throws Exception {
        assertEquals(401, get("/me", null).statusCode());
        assertEquals(401, get("/me", "token.invent.ado").statusCode());
    }

    @Test
    @DisplayName("aluno não acessa rota de professor e vice-versa")
    void cadaTipoNaSuaArea() throws Exception {
        post("/cadastro", """
                {"nome":"Aluno Teste","email":"aluno.area@teste.com","senha":"senha123","tipo":"aluno"}
                """);
        post("/cadastro", """
                {"nome":"Prof Teste","email":"prof.area@teste.com","senha":"senha123","tipo":"professor"}
                """);

        String tokenAluno = token("aluno.area@teste.com");
        String tokenProfessor = token("prof.area@teste.com");

        assertEquals(200, get("/aluno/aulas", tokenAluno).statusCode());
        assertEquals(403, get("/professor/agenda", tokenAluno).statusCode());

        assertEquals(200, get("/professor/agenda", tokenProfessor).statusCode());
        assertEquals(403, get("/aluno/aulas", tokenProfessor).statusCode());
    }

    // ----- ajudantes -----

    private static HttpResponse<String> post(String caminho, String json) throws Exception {
        HttpRequest requisicao = HttpRequest.newBuilder()
                .uri(URI.create(base + caminho))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
        return client.send(requisicao, HttpResponse.BodyHandlers.ofString());
    }

    private static HttpResponse<String> get(String caminho, String token) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(base + caminho))
                .GET();
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static String token(String email) throws Exception {
        HttpResponse<String> resposta = post("/login",
                "{\"email\":\"" + email + "\",\"senha\":\"senha123\"}");
        assertEquals(200, resposta.statusCode());
        return Document.parse(resposta.body()).getString("token");
    }

    private static void assertNotEqualsSenhaPura(String hash) {
        assertNotNull(hash);
        assertFalse("senha123".equals(hash), "a senha foi salva em texto puro no banco");
        assertTrue(hash.startsWith("$2a$") || hash.startsWith("$2y$") || hash.startsWith("$2b$"),
                "o hash salvo não parece BCrypt: " + hash);
    }
}