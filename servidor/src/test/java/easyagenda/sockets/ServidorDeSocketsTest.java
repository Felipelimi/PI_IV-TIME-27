package easyagenda.sockets;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.function.BooleanSupplier;

import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import easyagenda.Database;
import easyagenda.Server;
import easyagenda.aulas.Aulas;
import easyagenda.auth.Auth;
import easyagenda.auth.Usuarios;
import easyagenda.horarios.Horarios;

/**
 * Testes do servidor de sockets: os Comunicados indo e voltando, a lista de
 * conectados, o "desativar" e a API quando o servidor de sockets está fora do ar.
 *
 * Precisa do Mongo no ar: docker compose up -d mongo
 * Usa o banco "easyagenda_test", que é limpo antes e depois.
 */
class ServidorDeSocketsTest {

    private static final String BANCO_DE_TESTE = "easyagenda_test";
    private static final String DIA = LocalDate.now(Horarios.FUSO).plusDays(7).toString();

    private static int porta;
    private static final ObjectId PROFESSOR = new ObjectId();
    private static final String ALUNA = new ObjectId().toHexString();
    private static final String ALUNO = new ObjectId().toHexString();

    @BeforeAll
    static void subirTudo() throws Exception {
        String uri = System.getenv().getOrDefault("MONGODB_URI", "mongodb://localhost:27017");
        try {
            Database.connect(uri, BANCO_DE_TESTE);
        } catch (RuntimeException e) {
            fail("Não consegui conectar no MongoDB (" + uri + "). Suba o banco com: docker compose up -d mongo");
        }
        porta = ServidorDeSocketsDeTeste.subir();
    }

    @BeforeEach
    void limparAgenda() {
        Horarios.colecao().drop();
        Aulas.colecao().drop();
        Aulas.garantirIndices(); // o drop leva o índice único junto
    }

    @AfterAll
    static void derrubarTudo() {
        Horarios.colecao().drop();
        Aulas.colecao().drop();
    }

    @Test
    @DisplayName("PedidoDeAgendamento volta como Resultado 201, e o segundo aluno no mesmo horário recebe 409")
    void agendarPeloSocket() throws Exception {
        String horarioId = criarHorario("08:00", "09:00");

        Resultado primeiro = ClienteDeSockets.pedir(new PedidoDeAgendamento(horarioId, ALUNA));
        assertEquals(201, primeiro.getCodigo());
        Document aula = Document.parse(primeiro.getJson());
        assertEquals("agendada", aula.getString("status"));
        assertEquals(ALUNA, aula.getString("alunoId"));

        Resultado segundo = ClienteDeSockets.pedir(new PedidoDeAgendamento(horarioId, ALUNO));
        assertEquals(409, segundo.getCodigo());
        assertEquals("horário indisponível", Document.parse(segundo.getJson()).getString("erro"));
    }

    @Test
    @DisplayName("pedido sem horarioId recebe Resultado 400")
    void pedidoSemHorario() throws Exception {
        Resultado resultado = ClienteDeSockets.pedir(new PedidoDeAgendamento(null, ALUNA));
        assertEquals(400, resultado.getCodigo());
    }

    @Test
    @DisplayName("cancelar e remarcar pelo socket")
    void cancelarERemarcarPeloSocket() throws Exception {
        String h1 = criarHorario("08:00", "09:00");
        String h2 = criarHorario("09:00", "10:00");
        String aulaId = Document.parse(ClienteDeSockets.pedir(new PedidoDeAgendamento(h1, ALUNA)).getJson())
                .getString("id");

        Resultado remarcada = ClienteDeSockets.pedir(new PedidoDeRemarcacao(aulaId, h2, ALUNA, Usuarios.ALUNO));
        assertEquals(201, remarcada.getCodigo());
        String novaId = Document.parse(remarcada.getJson()).getString("id");

        // Outro aluno não mexe na aula da Marina
        Resultado deOutro = ClienteDeSockets.pedir(new PedidoDeCancelamento(novaId, ALUNO, Usuarios.ALUNO));
        assertEquals(404, deOutro.getCodigo());

        Resultado cancelada = ClienteDeSockets.pedir(new PedidoDeCancelamento(novaId, ALUNA, Usuarios.ALUNO));
        assertEquals(200, cancelada.getCodigo());
        assertEquals("cancelada", Document.parse(cancelada.getJson()).getString("status"));
    }

    @Test
    @DisplayName("quem conecta entra na lista de usuários e sai dela com PedidoParaSair")
    void listaDeConectados() throws Exception {
        int antes = conectados();
        Parceiro servidor = conectar();
        esperar(() -> conectados() == antes + 1);

        servidor.receba(new PedidoParaSair());
        esperar(() -> conectados() == antes);
        servidor.adeus();
    }

    @Test
    @DisplayName("quem cai sem se despedir também sai da lista")
    void quedaSemDespedida() throws Exception {
        int antes = conectados();
        Parceiro servidor = conectar();
        esperar(() -> conectados() == antes + 1);

        servidor.adeus();
        esperar(() -> conectados() == antes);
    }

    @Test
    @DisplayName("\"desativar\" manda ComunicadoDeDesligamento para quem está conectado")
    void desativar() throws Exception {
        ArrayList<Parceiro> usuarios = new ArrayList<>();
        AceitadoraDeConexao outra = new AceitadoraDeConexao("0", usuarios);
        outra.setDaemon(true);
        outra.start();

        Parceiro servidor = conectar(outra.getPorta());
        esperar(() -> tamanho(usuarios) == 1);

        Servidor.desativar(usuarios);

        assertInstanceOf(ComunicadoDeDesligamento.class, servidor.envie());
        esperar(() -> tamanho(usuarios) == 0);
    }

    @Test
    @DisplayName("objeto que não é Comunicado é recusado e a conexão é fechada")
    void objetoEstranho() throws Exception {
        int antes = conectados();
        try (Socket conexao = new Socket("localhost", porta)) {
            conexao.setSoTimeout(5_000);
            ObjectOutputStream transmissor = new ObjectOutputStream(conexao.getOutputStream());
            ObjectInputStream receptor = new ObjectInputStream(conexao.getInputStream());
            ArrayList<String> estranho = new ArrayList<>();
            estranho.add("não sou um Comunicado");
            // O servidor fecha sem responder: falha ao terminar de escrever ou ao ler a resposta
            assertThrows(Exception.class, () -> {
                transmissor.writeObject(estranho);
                transmissor.flush();
                receptor.readObject();
            });
        }
        esperar(() -> conectados() == antes);
    }

    @Test
    @DisplayName("API responde 503 quando o servidor de sockets está fora do ar")
    void apiSemServidorDeSockets() throws Exception {
        int portaFechada;
        try (ServerSocket livre = new ServerSocket(0)) {
            portaFechada = livre.getLocalPort();
        }
        Server api = new Server(0);
        api.start();
        try {
            ClienteDeSockets.configurar("localhost", portaFechada);
            HttpResponse<String> resposta = HttpClient.newHttpClient().send(
                    HttpRequest.newBuilder(URI.create("http://localhost:" + api.getPort() + "/aulas"))
                            .header("Authorization", "Bearer " + Auth.gerarToken(ALUNA, Usuarios.ALUNO))
                            .POST(HttpRequest.BodyPublishers.ofString("{\"horarioId\": \"" + new ObjectId() + "\"}"))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(503, resposta.statusCode());
            assertTrue(resposta.body().contains("indisponível"));
        } finally {
            ServidorDeSocketsDeTeste.subir(); // aponta de volta para o servidor de teste
            api.stop();
        }
    }

    // ----- ajudantes -----

    private static String criarHorario(String inicio, String fim) {
        return Horarios.criar(PROFESSOR, DIA, inicio, fim, Horarios.ONLINE).getObjectId("_id").toHexString();
    }

    private static Parceiro conectar() throws Exception {
        return conectar(porta);
    }

    /** Conecta como no Cliente do modelo: transmissor, receptor e Parceiro. */
    private static Parceiro conectar(int portaDoServidor) throws Exception {
        Socket conexao = new Socket("localhost", portaDoServidor);
        conexao.setSoTimeout(5_000);
        ObjectOutputStream transmissor = new ObjectOutputStream(conexao.getOutputStream());
        ObjectInputStream receptor = new ObjectInputStream(conexao.getInputStream());
        receptor.setObjectInputFilter(Comunicado.FILTRO);
        return new Parceiro(conexao, receptor, transmissor);
    }

    private static int conectados() {
        return tamanho(ServidorDeSocketsDeTeste.USUARIOS);
    }

    private static int tamanho(ArrayList<Parceiro> usuarios) {
        synchronized (usuarios) {
            return usuarios.size();
        }
    }

    /** A Supervisora roda em outra thread: espera até 3 s a condição ficar verdadeira. */
    private static void esperar(BooleanSupplier condicao) throws InterruptedException {
        for (int i = 0; i < 60 && !condicao.getAsBoolean(); i++) {
            Thread.sleep(50);
        }
        assertTrue(condicao.getAsBoolean(), "a condição não ficou verdadeira a tempo");
    }
}
