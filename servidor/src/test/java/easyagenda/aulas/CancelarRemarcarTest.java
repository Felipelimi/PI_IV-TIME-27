package easyagenda.aulas;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
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
 * Testes de cancelar e remarcar aula (tarefa 4.1).
 *
 * Precisa do Mongo no ar: docker compose up -d mongo
 * Usa o banco "easyagenda_test", que é limpo antes e depois.
 */
class CancelarRemarcarTest {

    private static final String BANCO_DE_TESTE = "easyagenda_test";

    // Sempre uma semana no futuro: bem longe do prazo de 24 h
    private static final String DIA = LocalDate.now(Horarios.FUSO).plusDays(7).toString();

    private static Server server;
    private static String base;
    private static final HttpClient client = HttpClient.newHttpClient();

    private static String tokenAna;     // professora
    private static String tokenBruno;   // outro professor
    private static String tokenMarina;  // aluna
    private static String tokenPedro;   // aluno
    private static String idAna;
    private static String idMarina;

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

        tokenAna = cadastrarEEntrar("Ana", "ana.cancelar@teste.com", "professor");
        tokenBruno = cadastrarEEntrar("Bruno", "bruno.cancelar@teste.com", "professor");
        tokenMarina = cadastrarEEntrar("Marina", "marina.cancelar@teste.com", "aluno");
        tokenPedro = cadastrarEEntrar("Pedro", "pedro.cancelar@teste.com", "aluno");
        idAna = Usuarios.buscarPorEmail("ana.cancelar@teste.com").getObjectId("_id").toHexString();
        idMarina = Usuarios.buscarPorEmail("marina.cancelar@teste.com").getObjectId("_id").toHexString();
    }

    @BeforeEach
    void limparAgenda() {
        Horarios.colecao().drop();
        Aulas.colecao().drop();
        Aulas.garantirIndices(); // o drop leva o índice único junto
        // Volta o prazo da Ana para o padrão (sem o campo = 24 h)
        Usuarios.colecao().updateOne(new Document("_id", new ObjectId(idAna)),
                new Document("$unset", new Document("prazoCancelamentoHoras", "")));
    }

    @AfterAll
    static void derrubarTudo() {
        Usuarios.colecao().drop();
        Horarios.colecao().drop();
        Aulas.colecao().drop();
        server.stop();
    }

    // ----- cancelar -----

    @Test
    @DisplayName("cancelar dentro do prazo: a aula fica cancelada e o horário volta a ficar livre")
    void cancelarDentroDoPrazo() throws Exception {
        String horarioId = criarHorario(tokenAna, "10:00", "11:00");
        String aulaId = agendar(horarioId, tokenMarina);
        assertTrue(horariosLivresDaAna().isEmpty());

        HttpResponse<String> resposta = cancelar(aulaId, tokenMarina);

        assertEquals(200, resposta.statusCode(), resposta.body());
        assertEquals("cancelada", Document.parse(resposta.body()).getString("status"));
        assertEquals("cancelada", aulaNoBanco(aulaId).getString("status"));
        assertTrue(aulaNoBanco(aulaId).get("canceladaEm") instanceof Date);
        assertEquals(List.of(horarioId), idsDosLivres());

        // Outro aluno já consegue marcar o horário
        agendar(horarioId, tokenPedro);

        // Cancelar de novo não faz nada
        HttpResponse<String> deNovo = cancelar(aulaId, tokenMarina);
        assertEquals(409, deNovo.statusCode());
        assertEquals("essa aula não está mais agendada", erro(deNovo));
    }

    @Test
    @DisplayName("cancelar fora do prazo: devolve a mensagem e a aula continua marcada")
    void cancelarForaDoPrazo() throws Exception {
        String aulaId = aulaDaquiA(Duration.ofHours(5)); // prazo padrão é 24 h

        HttpResponse<String> resposta = cancelar(aulaId, tokenMarina);

        assertEquals(409, resposta.statusCode());
        assertEquals("não dá mais para cancelar: o professor pede pelo menos 24 h de antecedência", erro(resposta));
        assertEquals("agendada", aulaNoBanco(aulaId).getString("status"));
    }

    @Test
    @DisplayName("o prazo é o que o professor definiu")
    void prazoDoProfessor() throws Exception {
        assertEquals(24, prazo(tokenAna));

        HttpResponse<String> mudou = mudarPrazo("{\"prazoCancelamentoHoras\": 2}", tokenAna);
        assertEquals(200, mudou.statusCode(), mudou.body());
        assertEquals(2, prazo(tokenAna));

        // Com prazo de 2 h, uma aula daqui a 3 h pode ser cancelada...
        String aulaEm3h = aulaDaquiA(Duration.ofHours(3));
        assertEquals(200, cancelar(aulaEm3h, tokenMarina).statusCode());

        // ...e uma daqui a 1 h, não
        String aulaEm1h = aulaDaquiA(Duration.ofHours(1));
        HttpResponse<String> recusada = cancelar(aulaEm1h, tokenMarina);
        assertEquals(409, recusada.statusCode());
        assertEquals("não dá mais para cancelar: o professor pede pelo menos 2 h de antecedência", erro(recusada));

        // O prazo aparece no link do professor, para o aluno saber a regra antes de marcar
        HttpResponse<String> link = enviar("GET", "/professores/" + idAna + "/horarios?de=" + DIA + "&ate=" + DIA, null, null);
        assertEquals(2, Document.parse(link.body()).get("professor", Document.class).getInteger("prazoCancelamentoHoras"));
    }

    @Test
    @DisplayName("prazo inválido ou mudado por quem não é professor é recusado")
    void prazoInvalido() throws Exception {
        assertEquals(400, mudarPrazo("{\"prazoCancelamentoHoras\": -1}", tokenAna).statusCode());
        assertEquals(400, mudarPrazo("{\"prazoCancelamentoHoras\": 721}", tokenAna).statusCode());
        assertEquals(400, mudarPrazo("{\"prazoCancelamentoHoras\": 1.5}", tokenAna).statusCode());
        assertEquals(400, mudarPrazo("{\"prazoCancelamentoHoras\": \"24\"}", tokenAna).statusCode());
        assertEquals(400, mudarPrazo("{}", tokenAna).statusCode());
        assertEquals(403, mudarPrazo("{\"prazoCancelamentoHoras\": 1}", tokenMarina).statusCode());
        assertEquals(401, mudarPrazo("{\"prazoCancelamentoHoras\": 1}", null).statusCode());
        assertEquals(24, prazo(tokenAna));

        // 0 é aceito: pode cancelar até a aula começar
        assertEquals(200, mudarPrazo("{\"prazoCancelamentoHoras\": 0}", tokenAna).statusCode());
        assertEquals(0, prazo(tokenAna));
    }

    @Test
    @DisplayName("só o aluno da aula cancela, e aula que já começou não pode ser cancelada")
    void regrasDoCancelamento() throws Exception {
        String aulaId = agendar(criarHorario(tokenAna, "10:00", "11:00"), tokenMarina);

        assertEquals(401, cancelar(aulaId, null).statusCode());
        assertEquals(403, cancelar(aulaId, tokenAna).statusCode());     // professor não usa esta rota
        assertEquals(404, cancelar(aulaId, tokenPedro).statusCode());   // aula de outro aluno
        assertEquals(404, cancelar("000000000000000000000000", tokenMarina).statusCode());
        assertEquals(404, cancelar("nao-e-um-id", tokenMarina).statusCode());
        assertEquals(404, enviar("POST", "/aulas/" + aulaId + "/apagar", null, tokenMarina).statusCode());
        assertEquals("agendada", aulaNoBanco(aulaId).getString("status"));

        mudarPrazo("{\"prazoCancelamentoHoras\": 0}", tokenAna);
        HttpResponse<String> comecou = cancelar(aulaDaquiA(Duration.ofMinutes(-10)), tokenMarina);
        assertEquals(409, comecou.statusCode());
        assertEquals("essa aula já começou", erro(comecou));
    }

    // ----- remarcar -----

    @Test
    @DisplayName("remarcar: agenda o horário novo, cancela o antigo e libera o horário antigo")
    void remarcar() throws Exception {
        String horarioAntigo = criarHorario(tokenAna, "10:00", "11:00");
        String horarioNovo = criarHorario(tokenAna, "15:00", "16:00");
        String aulaId = agendar(horarioAntigo, tokenMarina);

        HttpResponse<String> resposta = remarcar(aulaId, horarioNovo, tokenMarina);

        assertEquals(201, resposta.statusCode(), resposta.body());
        Document nova = Document.parse(resposta.body());
        assertEquals("agendada", nova.getString("status"));
        assertEquals("15:00", nova.getString("horaInicio"));
        assertEquals(idMarina, nova.getString("alunoId"));
        assertEquals(aulaId, nova.getString("remarcadaDe"));

        assertEquals("cancelada", aulaNoBanco(aulaId).getString("status"));
        assertEquals("agendada", aulaNoBanco(nova.getString("id")).getString("status"));
        assertEquals(List.of(horarioAntigo), idsDosLivres());
    }

    @Test
    @DisplayName("remarcar para um horário ocupado: 409 e a aula antiga continua marcada")
    void remarcarParaHorarioOcupado() throws Exception {
        String horarioDaMarina = criarHorario(tokenAna, "10:00", "11:00");
        String horarioDoPedro = criarHorario(tokenAna, "15:00", "16:00");
        String aulaDaMarina = agendar(horarioDaMarina, tokenMarina);
        String aulaDoPedro = agendar(horarioDoPedro, tokenPedro);

        HttpResponse<String> resposta = remarcar(aulaDaMarina, horarioDoPedro, tokenMarina);

        assertEquals(409, resposta.statusCode());
        assertEquals("horário indisponível", erro(resposta));
        assertEquals("agendada", aulaNoBanco(aulaDaMarina).getString("status"));
        assertEquals("agendada", aulaNoBanco(aulaDoPedro).getString("status"));
        assertEquals(2, Aulas.colecao().countDocuments());
    }

    @Test
    @DisplayName("remarcar fora do prazo é recusado e nada muda")
    void remarcarForaDoPrazo() throws Exception {
        String aulaId = aulaDaquiA(Duration.ofHours(5));
        String horarioNovo = criarHorario(tokenAna, "15:00", "16:00");

        HttpResponse<String> resposta = remarcar(aulaId, horarioNovo, tokenMarina);

        assertEquals(409, resposta.statusCode());
        assertEquals("não dá mais para remarcar: o professor pede pelo menos 24 h de antecedência", erro(resposta));
        assertEquals("agendada", aulaNoBanco(aulaId).getString("status"));
        assertEquals(1, Aulas.colecao().countDocuments());
        assertEquals(List.of(horarioNovo), idsDosLivres());
    }

    @Test
    @DisplayName("remarcar só para outro horário do mesmo professor")
    void regrasDaRemarcacao() throws Exception {
        String horarioAntigo = criarHorario(tokenAna, "10:00", "11:00");
        String horarioDoBruno = criarHorario(tokenBruno, "15:00", "16:00");
        String aulaId = agendar(horarioAntigo, tokenMarina);

        HttpResponse<String> outroProfessor = remarcar(aulaId, horarioDoBruno, tokenMarina);
        assertEquals(400, outroProfessor.statusCode());
        assertEquals("escolha um horário do mesmo professor da aula", erro(outroProfessor));

        HttpResponse<String> mesmoHorario = remarcar(aulaId, horarioAntigo, tokenMarina);
        assertEquals(400, mesmoHorario.statusCode());
        assertEquals("esse já é o horário da sua aula", erro(mesmoHorario));

        assertEquals(404, remarcar(aulaId, "000000000000000000000000", tokenMarina).statusCode());
        assertEquals(400, enviar("POST", "/aulas/" + aulaId + "/remarcar", "{}", tokenMarina).statusCode());
        assertEquals(404, remarcar(aulaId, horarioDoBruno, tokenPedro).statusCode()); // aula de outro aluno

        assertEquals("agendada", aulaNoBanco(aulaId).getString("status"));
        assertEquals(1, Aulas.colecao().countDocuments());
    }

    // ----- ajudantes -----

    /** Professor cria o horário pela rota da 2.2 e devolve o id. */
    private static String criarHorario(String token, String inicio, String fim) throws Exception {
        HttpResponse<String> resposta = enviar("POST", "/horarios",
                "{\"dia\":\"" + DIA + "\",\"horaInicio\":\"" + inicio + "\",\"horaFim\":\"" + fim
                        + "\",\"modalidade\":\"online\"}", token);
        assertEquals(201, resposta.statusCode(), "criar horário falhou: " + resposta.body());
        return Document.parse(resposta.body()).getString("id");
    }

    /** Aluno agenda pela rota da 3.1 e devolve o id da aula. */
    private static String agendar(String horarioId, String token) throws Exception {
        HttpResponse<String> resposta = enviar("POST", "/aulas", "{\"horarioId\":\"" + horarioId + "\"}", token);
        assertEquals(201, resposta.statusCode(), "agendar falhou: " + resposta.body());
        return Document.parse(resposta.body()).getString("id");
    }

    /** Aula da Marina com a Ana gravada direto no banco, para testar horários perto de agora. */
    private static String aulaDaquiA(Duration quanto) {
        Document aula = new Document()
                .append("professorId", new ObjectId(idAna))
                .append("alunoId", new ObjectId(idMarina))
                .append("dataHora", Date.from(Instant.now().plus(quanto)))
                .append("status", "agendada")
                .append("modalidade", "online");
        Aulas.colecao().insertOne(aula);
        return aula.getObjectId("_id").toHexString();
    }

    private static Document aulaNoBanco(String id) {
        return Aulas.colecao().find(new Document("_id", new ObjectId(id))).first();
    }

    private static List<Document> horariosLivresDaAna() throws Exception {
        HttpResponse<String> resposta = enviar("GET",
                "/professores/" + idAna + "/horarios?de=" + DIA + "&ate=" + DIA, null, null);
        assertEquals(200, resposta.statusCode());
        return Document.parse(resposta.body()).getList("horarios", Document.class);
    }

    private static List<String> idsDosLivres() throws Exception {
        return horariosLivresDaAna().stream().map(h -> h.getString("id")).toList();
    }

    private static HttpResponse<String> cancelar(String aulaId, String token) throws Exception {
        return enviar("POST", "/aulas/" + aulaId + "/cancelar", null, token);
    }

    private static HttpResponse<String> remarcar(String aulaId, String horarioId, String token) throws Exception {
        return enviar("POST", "/aulas/" + aulaId + "/remarcar", "{\"horarioId\":\"" + horarioId + "\"}", token);
    }

    private static int prazo(String token) throws Exception {
        HttpResponse<String> resposta = enviar("GET", "/professor/prazo-cancelamento", null, token);
        assertEquals(200, resposta.statusCode(), resposta.body());
        return Document.parse(resposta.body()).getInteger("prazoCancelamentoHoras");
    }

    private static HttpResponse<String> mudarPrazo(String json, String token) throws Exception {
        return enviar("PUT", "/professor/prazo-cancelamento", json, token);
    }

    private static String erro(HttpResponse<String> resposta) {
        return Document.parse(resposta.body()).getString("erro");
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
