package easyagenda;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;

/**
 * Conexão única com o MongoDB, compartilhada pelo resto da aplicação.
 */
public final class Database {

    private static MongoClient client;
    private static MongoDatabase database;

    private Database() {
    }

    public static void connect(String uri, String dbName) {
        client = MongoClients.create(uri);
        database = client.getDatabase(dbName);
        // Força uma ida ao banco para falhar logo se a URI estiver errada
        database.runCommand(new Document("ping", 1));
        System.out.println("MongoDB conectado: " + dbName);
    }

    public static MongoDatabase get() {
        if (database == null) {
            throw new IllegalStateException("Banco não conectado. Chame Database.connect() antes.");
        }
        return database;
    }

    public static void close() {
        if (client != null) {
            client.close();
        }
    }
}
