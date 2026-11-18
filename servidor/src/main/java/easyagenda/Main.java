package easyagenda;

import easyagenda.aulas.Aulas;
import easyagenda.auth.Usuarios;
import easyagenda.lembretes.EnvioDeEmail;
import easyagenda.lembretes.Lembretes;

import java.time.Duration;

/**
 * Ponto de entrada: conecta no MongoDB e sobe o servidor HTTP.
 */
public class Main {

    public static void main(String[] args) throws Exception {
        int port = Integer.parseInt(env("PORT", "8080"));
        String mongoUri = env("MONGODB_URI", "mongodb://localhost:27017");
        String dbName = env("MONGODB_DB", "easyagenda");

        Database.connect(mongoUri, dbName);
        Usuarios.garantirIndices();
        Aulas.garantirIndices();

        Server server = new Server(port);
        server.start();
        System.out.println("Servidor rodando em http://localhost:" + server.getPort());

        // Lembrete por e-mail das aulas (tarefa 3.3)
        Duration antecedencia = Duration.ofHours(Long.parseLong(env("LEMBRETE_ANTECEDENCIA_HORAS", "24")));
        Duration intervalo = Duration.ofMinutes(Long.parseLong(env("LEMBRETE_INTERVALO_MINUTOS", "10")));
        new Lembretes(EnvioDeEmail.doAmbiente(), antecedencia).iniciar(intervalo);
        System.out.println("Lembretes: aulas nas próximas " + antecedencia.toHours()
                + " h, conferidas a cada " + intervalo.toMinutes() + " min.");
    }

    private static String env(String name, String defaultValue) {
        String value = System.getenv(name);
        return (value == null || value.isBlank()) ? defaultValue : value;
    }
}