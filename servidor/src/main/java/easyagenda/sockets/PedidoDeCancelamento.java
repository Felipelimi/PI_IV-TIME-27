package easyagenda.sockets;

/** Aluno ou professor da aula quer cancelá-la (tarefa 4.1). */
public class PedidoDeCancelamento extends Comunicado {

    private static final long serialVersionUID = 1L;

    private final String aulaId;
    private final String usuarioId;
    private final String tipoUsuario; // "professor" ou "aluno"

    public PedidoDeCancelamento(String aulaId, String usuarioId, String tipoUsuario) {
        this.aulaId = aulaId;
        this.usuarioId = usuarioId;
        this.tipoUsuario = tipoUsuario;
    }

    public String getAulaId() {
        return this.aulaId;
    }

    public String getUsuarioId() {
        return this.usuarioId;
    }

    public String getTipoUsuario() {
        return this.tipoUsuario;
    }

    @Override
    public String toString() {
        return "cancelar aula " + this.aulaId + " (" + this.tipoUsuario + " " + this.usuarioId + ")";
    }
}
