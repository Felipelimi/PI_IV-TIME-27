package easyagenda;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

/**
 * Servidor HTTP usando o HttpServer que já vem no JDK (sem framework).
 * Novas rotas são registradas em registerRoutes().
 */
public class Server {

    private final HttpServer httpServer;

    public Server(int port) throws IOException {
        this.httpServer = HttpServer.create(new InetSocketAddress(port), 0);
        registerRoutes();
    }

    private void registerRoutes() {
        // Rota de teste: confirma que a API está no ar
        httpServer.createContext("/ping", exchange -> {
            if (!"GET".equals(exchange.getRequestMethod())) {
                send(exchange, 405, "método não permitido");
                return;
            }
            send(exchange, 200, "ok");
        });
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
