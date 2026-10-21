package easyagenda;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import org.bson.Document;

import com.sun.net.httpserver.HttpExchange;

/**
 * Ajudantes para ler o corpo da requisição e responder em JSON.
 * Usamos o Document do BSON (que já vem com o driver do Mongo) como nosso JSON,
 * assim não precisamos de mais nenhuma biblioteca.
 */
public final class Json {

    private Json() {
    }

    /**
     * Lê o corpo da requisição como JSON.
     *
     * @return o documento lido, ou null se o corpo estiver vazio ou mal formado.
     */
    public static Document lerCorpo(HttpExchange exchange) throws IOException {
        String texto = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8).trim();
        if (texto.isEmpty()) {
            return null;
        }
        try {
            return Document.parse(texto);
        } catch (RuntimeException e) {
            return null; // JSON inválido
        }
    }

    /** Lê um campo de texto do JSON, já sem espaços nas pontas. Null se não existir. */
    public static String texto(Document corpo, String campo) {
        Object valor = corpo.get(campo);
        if (!(valor instanceof String s)) {
            return null;
        }
        String limpo = s.trim();
        return limpo.isEmpty() ? null : limpo;
    }

    public static void enviar(HttpExchange exchange, int status, Document corpo) throws IOException {
        byte[] bytes = corpo.toJson().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    /** Resposta de erro no formato {"erro": "mensagem"}. */
    public static void erro(HttpExchange exchange, int status, String mensagem) throws IOException {
        enviar(exchange, status, new Document("erro", mensagem));
    }
}