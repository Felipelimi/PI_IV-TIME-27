package easyagenda.sockets;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.concurrent.Semaphore;

/**
 * A outra ponta de uma conexão (igual ao modelo da disciplina).
 * No servidor de sockets, cada Parceiro é um cliente conectado;
 * na API, o Parceiro é o servidor de sockets.
 */
public class Parceiro {

    private final Socket conexao;
    private final ObjectInputStream receptor;
    private final ObjectOutputStream transmissor;

    private Comunicado proximoComunicado = null;

    private final Semaphore mutEx = new Semaphore(1, true);

    public Parceiro(Socket conexao, ObjectInputStream receptor, ObjectOutputStream transmissor)
            throws Exception { // se parametros nulos
        if (conexao == null) {
            throw new Exception("Conexao ausente");
        }
        if (receptor == null) {
            throw new Exception("Receptor ausente");
        }
        if (transmissor == null) {
            throw new Exception("Transmissor ausente");
        }

        this.conexao = conexao;
        this.receptor = receptor;
        this.transmissor = transmissor;
    }

    /** O parceiro recebe o comunicado: escreve no socket. */
    public void receba(Comunicado x) throws Exception {
        try {
            this.transmissor.writeObject(x);
            this.transmissor.flush();
        } catch (IOException erro) {
            throw new Exception("Erro de transmissao");
        }
    }

    /** Olha o próximo comunicado sem tirá-lo da fila. */
    public Comunicado espie() throws Exception {
        this.mutEx.acquireUninterruptibly();
        try {
            if (this.proximoComunicado == null) {
                this.proximoComunicado = (Comunicado) this.receptor.readObject();
            }
            return this.proximoComunicado;
        } catch (Exception erro) {
            throw new Exception("Erro de recepcao");
        } finally {
            this.mutEx.release(); // no modelo o release ficava só no caminho sem erro
        }
    }

    /** O parceiro envia o próximo comunicado: lê do socket. */
    public Comunicado envie() throws Exception {
        try {
            if (this.proximoComunicado == null) {
                this.proximoComunicado = (Comunicado) this.receptor.readObject();
            }
            Comunicado ret = this.proximoComunicado;
            this.proximoComunicado = null;
            return ret;
        } catch (Exception erro) {
            throw new Exception("Erro de recepcao");
        }
    }

    public void adeus() throws Exception {
        try {
            this.transmissor.close();
            this.receptor.close();
            this.conexao.close();
        } catch (Exception erro) {
            throw new Exception("Erro de desconexao");
        }
    }
}
