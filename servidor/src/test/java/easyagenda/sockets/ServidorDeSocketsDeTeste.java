package easyagenda.sockets;

import java.util.ArrayList;

/**
 * Sobe o servidor de sockets dentro do próprio teste, numa porta livre,
 * e aponta a API para ele. Usa o banco que o teste já conectou.
 * Pode ser chamado por vários testes: sobe uma vez só.
 */
public final class ServidorDeSocketsDeTeste {

    /** Os conectados agora, como a lista "usuarios" do Servidor. */
    public static final ArrayList<Parceiro> USUARIOS = new ArrayList<>();

    private static AceitadoraDeConexao aceitadora;

    private ServidorDeSocketsDeTeste() {
    }

    public static synchronized int subir() throws Exception {
        if (aceitadora == null) {
            aceitadora = new AceitadoraDeConexao("0", USUARIOS); // 0 = o sistema escolhe uma porta livre
            aceitadora.setDaemon(true);
            aceitadora.start();
        }
        ClienteDeSockets.configurar("localhost", aceitadora.getPorta());
        return aceitadora.getPorta();
    }
}
