package easyagenda.sockets;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.ArrayList;

/**
 * Uma thread por cliente conectado: lê os pedidos dele, um de cada vez,
 * e devolve um Resultado para cada um (igual ao modelo da disciplina).
 */
public class SupervisoraDeConexao extends Thread {

    private Parceiro usuario;
    private final Socket conexao;
    private final ArrayList<Parceiro> usuarios;

    public SupervisoraDeConexao(Socket conexao, ArrayList<Parceiro> usuarios) throws Exception {
        if (conexao == null) {
            throw new Exception("Conexao ausente");
        }
        if (usuarios == null) {
            throw new Exception("Usuarios ausentes");
        }

        this.conexao = conexao;
        this.usuarios = usuarios;
    }

    @Override
    public void run() {
        ObjectOutputStream transmissor;
        try {
            transmissor = new ObjectOutputStream(this.conexao.getOutputStream());
        } catch (Exception erro) {
            fechar(null, null);
            return;
        }

        ObjectInputStream receptor;
        try {
            receptor = new ObjectInputStream(this.conexao.getInputStream());
            receptor.setObjectInputFilter(Comunicado.FILTRO);
        } catch (Exception erro) {
            fechar(transmissor, null);
            return;
        }

        try {
            this.usuario = new Parceiro(this.conexao, receptor, transmissor);
        } catch (Exception erro) {
        } // sei que passei os parametros corretos

        try {
            synchronized (this.usuarios) {
                this.usuarios.add(this.usuario);
            }

            for (;;) {
                Comunicado comunicado = this.usuario.envie();

                if (comunicado == null) {
                    return;
                } else if (comunicado instanceof PedidoDeAgendamento) {
                    PedidoDeAgendamento pedido = (PedidoDeAgendamento) comunicado;
                    this.usuario.receba(atender(pedido));
                } else if (comunicado instanceof PedidoDeCancelamento) {
                    PedidoDeCancelamento pedido = (PedidoDeCancelamento) comunicado;
                    this.usuario.receba(atender(pedido));
                } else if (comunicado instanceof PedidoDeRemarcacao) {
                    PedidoDeRemarcacao pedido = (PedidoDeRemarcacao) comunicado;
                    this.usuario.receba(atender(pedido));
                } else if (comunicado instanceof PedidoParaSair) {
                    synchronized (this.usuarios) {
                        this.usuarios.remove(this.usuario);
                    }
                    this.usuario.adeus();
                    return;
                }
            }
        } catch (Exception erro) {
            // O cliente caiu sem mandar PedidoParaSair (ou mandou algo que não é Comunicado)
            synchronized (this.usuarios) {
                this.usuarios.remove(this.usuario);
            }
            fechar(transmissor, receptor);
        }
    }

    /**
     * Executa o pedido. Um erro inesperado (ex.: o banco caiu) vira um
     * Resultado 500, e a conexão continua de pé para os próximos pedidos.
     */
    private Resultado atender(Comunicado pedido) {
        try {
            if (pedido instanceof PedidoDeAgendamento) {
                return OperacoesDeAula.agendar((PedidoDeAgendamento) pedido);
            } else if (pedido instanceof PedidoDeCancelamento) {
                return OperacoesDeAula.cancelar((PedidoDeCancelamento) pedido);
            } else {
                return OperacoesDeAula.remarcar((PedidoDeRemarcacao) pedido);
            }
        } catch (RuntimeException erro) {
            System.err.println("Erro ao atender \"" + pedido + "\": " + erro);
            return Resultado.erro(500, "erro interno no servidor");
        }
    }

    private void fechar(ObjectOutputStream transmissor, ObjectInputStream receptor) {
        try {
            if (transmissor != null) {
                transmissor.close();
            }
            if (receptor != null) {
                receptor.close();
            }
            this.conexao.close();
        } catch (Exception falha) {
        } // so tentando fechar antes de acabar a thread
    }
}
