package easyagenda;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import easyagenda.auth.Auth;
import easyagenda.auth.AuthRoutes;
import easyagenda.auth.Usuarios;
import easyagenda.horarios.HorarioRoutes;
import org.bson.Document;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Servidor HTTP usando o HttpServer que já vem no JDK (sem framework).
 * Novas rotas são registradas em registerRoutes().
 *
 * Use rota(...) para rota aberta e rotaProtegida(...) para rota que exige token.
 */
public class Server {

    private final HttpServer httpServer;

    // Para cada caminho, qual handler atende cada método (GET, POST, PUT, DELETE)
    private final Map<String, Map<String, HttpHandler>> rotas = new HashMap<>();

    public Server(int port) throws IOException {
        this.httpServer = HttpServer.create(new InetSocketAddress(port), 0);
        registerRoutes();
    }

    private void registerRoutes() {
        // Rota de teste: confirma que a API está no ar
        rota("/ping", "GET", exchange -> send(exchange, 200, "ok"));

        // Autenticação
        rota("/cadastro", "POST", AuthRoutes::cadastrar);
        rota("/login", "POST", AuthRoutes::entrar);

        // Qualquer usuário logado: devolve quem está com o token
        rotaProtegida("/me", null, "GET", exchange -> {
            Auth.Usuario usuario = usuarioLogado(exchange);
            Json.enviar(exchange, 200, new Document()
                    .append("id", usuario.id())
                    .append("tipo", usuario.tipo()));
        });

        // Exemplos de rota por tipo — substitua pelas rotas reais de agenda/aulas
        rotaProtegida("/professor/agenda", Usuarios.PROFESSOR, "GET", exchange ->
                Json.enviar(exchange, 200, new Document("mensagem", "área do professor")));

        rotaProtegida("/aluno/aulas", Usuarios.ALUNO, "GET", exchange ->
                Json.enviar(exchange, 200, new Document("mensagem", "área do aluno")));

        // Horários do professor (tarefa 2.2)
        rotaProtegida("/horarios", Usuarios.PROFESSOR, "GET", HorarioRoutes::listarMeus);
        rotaProtegida("/horarios", Usuarios.PROFESSOR, "POST", HorarioRoutes::criar);
        rotaProtegida("/horarios", Usuarios.PROFESSOR, "PUT", HorarioRoutes::mudarModalidade);
        rotaProtegida("/horarios", Usuarios.PROFESSOR, "DELETE", HorarioRoutes::remover);

        // Rota aberta: o "link" do professor, com os horários livres para o aluno
        rota("/professores", "GET", HorarioRoutes::listarDoProfessor);
    }

    /**
     * Rota aberta. O mesmo caminho pode ter um handler para cada método
     * (ex.: GET e POST em /horarios). Trata erro inesperado e fecha a conexão.
     */
    private void rota(String caminho, String metodo, HttpHandler handler) {
        if (!rotas.containsKey(caminho)) {
            rotas.put(caminho, new HashMap<>());
            httpServer.createContext(caminho, exchange -> atender(caminho, exchange));
        }
        rotas.get(caminho).put(metodo, handler);
    }

    private void atender(String caminho, HttpExchange exchange) throws IOException {
        try {
            HttpHandler handler = rotas.get(caminho).get(exchange.getRequestMethod());
            if (handler == null) {
                Json.erro(exchange, 405, "método não permitido");
                return;
            }
            handler.handle(exchange);
        } catch (Exception e) {
            e.printStackTrace();
            try {
                Json.erro(exchange, 500, "erro interno no servidor");
            } catch (IOException ignorado) {
                // resposta já começou a ser enviada
            }
        } finally {
            exchange.close();
        }
    }

    /**
     * Rota protegida: sem token válido ninguém entra.
     *
     * @param tipoPermitido "professor", "aluno" ou null para qualquer usuário logado.
     */
    private void rotaProtegida(String caminho, String tipoPermitido, String metodo, HttpHandler handler) {
        rota(caminho, metodo, exchange -> {
            Auth.Usuario usuario = Auth.usuarioDaRequisicao(exchange);
            if (usuario == null) {
                Json.erro(exchange, 401, "token ausente ou inválido");
                return;
            }
            if (tipoPermitido != null && !tipoPermitido.equals(usuario.tipo())) {
                Json.erro(exchange, 403, "acesso permitido apenas para " + tipoPermitido);
                return;
            }
            exchange.setAttribute("usuario", usuario);
            handler.handle(exchange);
        });
    }

    /** Dentro de uma rota protegida, devolve quem está logado. */
    public static Auth.Usuario usuarioLogado(HttpExchange exchange) {
        return (Auth.Usuario) exchange.getAttribute("usuario");
    }

    public void start() {
        httpServer.start();
    }

    public void stop() {
        httpServer.stop(0);
    }

    public int getPort() {
        return httpServer.getAddress().getPort();
    }

    static void send(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}