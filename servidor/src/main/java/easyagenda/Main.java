package easyagenda;

/**
 * Ponto de entrada: conecta no MongoDB e sobe o servidor HTTP.
 */
public class Main {

    public static void main(String[] args) throws Exception {
        int port = Integer.parseInt(env("PORT", "8080"));
        String mongoUri = env("MONGODB_URI", "mongodb://localhost:27017");
        String dbName = env("MONGODB_DB", "easyagenda");

        Database.connect(mongoUri, dbName);

        Server server = new Server(port);
        server.start();
        System.out.println("Servidor rodando em http://localhost:" + server.getPort());
    }

    private static String env(String name, String defaultValue) {
        String value = System.getenv(name);
        return (value == null || value.isBlank()) ? defaultValue : value;
    }
}
