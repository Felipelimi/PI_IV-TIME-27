package easyagenda.sockets;

import java.util.ArrayList;

import easyagenda.Database;
import easyagenda.aulas.Aulas;

/**
 * Servidor de sockets do EasyAgenda (igual ao modelo da disciplina).
 *
 * É ele quem agenda, cancela e remarca aulas. A API HTTP (easyagenda.Main)
 * recebe os pedidos do site, confere o login e repassa a este servidor como
 * Comunicados (PedidoDeAgendamento, PedidoDeCancelamento, PedidoDeRemarcacao).
 *
 * Uso: java easyagenda.sockets.Servidor [PORTA]
 * Para desligar, digite "desativar": todos os conectados recebem um
 * ComunicadoDeDesligamento antes de a conexão ser fechada.
 */
public class Servidor {

    public static String PORTA_PADRAO = "3000";

    public static void main(String[] args) {
        if (args.length > 1) {
            System.err.println("Uso esperado: java easyagenda.sockets.Servidor [PORTA]\n");
            return;
        }

        String porta = env("SOCKETS_PORTA", Servidor.PORTA_PADRAO);
        if (args.length == 1) {
            porta = args[0];
        }

        try {
            Database.connect(env("MONGODB_URI", "mongodb://localhost:27017"), env("MONGODB_DB", "easyagenda"));
            Aulas.garantirIndices();
        } catch (RuntimeException erro) {
            System.err.println("Não consegui conectar no MongoDB: " + erro.getMessage());
            System.exit(1);
        }

        ArrayList<Parceiro> usuarios = new ArrayList<Parceiro>();

        AceitadoraDeConexao aceitadoraDeConexao = null;
        try {
            aceitadoraDeConexao = new AceitadoraDeConexao(porta, usuarios);
            aceitadoraDeConexao.start();
        } catch (Exception erro) {
            System.err.println("Escolha uma porta apropriada e liberada para uso!\n");
            System.exit(1);
        }

        // Se o processo for encerrado de fora (Ctrl+C, docker stop), avisa os conectados mesmo assim
        Runtime.getRuntime().addShutdownHook(new Thread(() -> desativar(usuarios)));

        System.out.println("Servidor de sockets ativo na porta " + aceitadoraDeConexao.getPorta());

        for (;;) {
            System.out.println("O servidor esta ativo! Para desativa-lo,");
            System.out.println("use o comando \"desativar\"\n");
            System.out.print("> ");

            String comando = Teclado.getUmString();

            if (comando == null) {
                // Sem teclado (Docker, Render): segue atendendo até o processo ser encerrado
                System.out.println("\nSem teclado: o servidor segue ativo até o processo ser encerrado.");
                return;
            }

            if (comando.trim().toLowerCase().equals("desativar")) {
                desativar(usuarios);
                System.out.println("O servidor foi desativado!\n");
                System.exit(0);
            } else {
                System.err.println("Comando invalido!\n");
            }
        }
    }

    /** Manda o ComunicadoDeDesligamento para cada conectado e fecha a conexão dele. */
    public static void desativar(ArrayList<Parceiro> usuarios) {
        synchronized (usuarios) {
            ComunicadoDeDesligamento comunicadoDeDesligamento = new ComunicadoDeDesligamento();

            for (Parceiro usuario : usuarios) {
                try {
                    usuario.receba(comunicadoDeDesligamento);
                    usuario.adeus();
                } catch (Exception erro) {
                }
            }
        }
    }

    private static String env(String nome, String padrao) {
        String valor = System.getenv(nome);
        return (valor == null || valor.isBlank()) ? padrao : valor;
    }
}
