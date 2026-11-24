package easyagenda.sockets;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * Lado cliente, usado pela API HTTP para falar com o servidor de sockets.
 *
 * Cada pedido abre a sua conexão: manda o Comunicado, espera o Resultado,
 * manda PedidoParaSair e fecha. Assim várias requisições do site são
 * atendidas ao mesmo tempo (uma SupervisoraDeConexao para cada), e se o
 * servidor de sockets reiniciar a API não fica presa a uma conexão morta.
 */
public final class ClienteDeSockets {

    private static final int ESPERA_PARA_CONECTAR_MS = 3_000;
    private static final int ESPERA_PELA_RESPOSTA_MS = 15_000;

    private static volatile String host = env("SOCKETS_HOST", "localhost");
    private static volatile int porta = Integer.parseInt(env("SOCKETS_PORTA", Servidor.PORTA_PADRAO));

    private ClienteDeSockets() {
    }

    /** Troca o endereço do servidor de sockets (os testes usam uma porta livre qualquer). */
    public static void configurar(String novoHost, int novaPorta) {
        host = novoHost;
        porta = novaPorta;
    }

    public static String endereco() {
        return host + ":" + porta;
    }

    /**
     * Manda o pedido e devolve o Resultado.
     *
     * @throws Exception se não der para falar com o servidor de sockets
     *                   (fora do ar, demorou demais ou está sendo desligado).
     */
    public static Resultado pedir(Comunicado pedido) throws Exception {
        Socket conexao = new Socket();
        Parceiro servidor = null;
        try {
            conexao.connect(new InetSocketAddress(host, porta), ESPERA_PARA_CONECTAR_MS);
            conexao.setSoTimeout(ESPERA_PELA_RESPOSTA_MS);

            ObjectOutputStream transmissor = new ObjectOutputStream(conexao.getOutputStream());
            ObjectInputStream receptor = new ObjectInputStream(conexao.getInputStream());
            receptor.setObjectInputFilter(Comunicado.FILTRO);
            servidor = new Parceiro(conexao, receptor, transmissor);

            servidor.receba(pedido);
            Comunicado resposta = servidor.envie();

            if (resposta instanceof ComunicadoDeDesligamento) {
                throw new Exception("o servidor de sockets está sendo desligado");
            }
            if (!(resposta instanceof Resultado)) {
                throw new Exception("resposta inesperada do servidor de sockets: " + resposta);
            }

            servidor.receba(new PedidoParaSair());
            return (Resultado) resposta;
        } finally {
            try {
                if (servidor != null) {
                    servidor.adeus();
                } else {
                    conexao.close();
                }
            } catch (Exception falha) {
            } // so tentando fechar
        }
    }

    private static String env(String nome, String padrao) {
        String valor = System.getenv(nome);
        return (valor == null || valor.isBlank()) ? padrao : valor;
    }
}
