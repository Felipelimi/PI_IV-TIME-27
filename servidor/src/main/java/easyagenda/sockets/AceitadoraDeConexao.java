package easyagenda.sockets;

import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;

/**
 * Fica aceitando conexões na porta e cria uma SupervisoraDeConexao
 * (uma thread) para cada cliente que conecta (igual ao modelo da disciplina).
 */
public class AceitadoraDeConexao extends Thread {

    private final ServerSocket pedido;
    private final ArrayList<Parceiro> usuarios;

    public AceitadoraDeConexao(String porta, ArrayList<Parceiro> usuarios) throws Exception {
        if (porta == null) {
            throw new Exception("Porta ausente");
        }

        try {
            // Só aceita conexões da própria máquina: quem fala com este servidor é a
            // API, que roda junto. Assim ninguém de fora chega na porta.
            this.pedido = new ServerSocket(Integer.parseInt(porta), 50, InetAddress.getLoopbackAddress());
        } catch (Exception erro) {
            throw new Exception("Porta invalida");
        }

        if (usuarios == null) {
            throw new Exception("Usuarios ausentes");
        }

        this.usuarios = usuarios;
    }

    /** Porta em que está escutando (útil quando se pede a porta "0", que o sistema escolhe). */
    public int getPorta() {
        return this.pedido.getLocalPort();
    }

    @Override
    public void run() {
        for (;;) {
            Socket conexao = null;
            try {
                conexao = this.pedido.accept();
            } catch (Exception erro) {
                if (this.pedido.isClosed()) {
                    return;
                }
                continue;
            }

            SupervisoraDeConexao supervisoraDeConexao = null;
            try {
                supervisoraDeConexao = new SupervisoraDeConexao(conexao, this.usuarios);
            } catch (Exception erro) {
            } // sei que passei parametros corretos para o construtor
            supervisoraDeConexao.start();
        }
    }
}
