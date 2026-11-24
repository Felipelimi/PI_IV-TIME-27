package easyagenda.sockets;

/** Aluno ou professor da aula quer passá-la para outro horário livre (tarefa 4.1). */
public class PedidoDeRemarcacao extends Comunicado {

    private static final long serialVersionUID = 1L;

    private final String aulaId;
    private final String horarioId; // pode vir null: o servidor responde o erro
    private final String usuarioId;
    private final String tipoUsuario; // "professor" ou "aluno"

    public PedidoDeRemarcacao(String aulaId, String horarioId, String usuarioId, String tipoUsuario) {
        this.aulaId = aulaId;
        this.horarioId = horarioId;
        this.usuarioId = usuarioId;
        this.tipoUsuario = tipoUsuario;
    }

    public String getAulaId() {
        return this.aulaId;
    }

    public String getHorarioId() {
        return this.horarioId;
    }

    public String getUsuarioId() {
        return this.usuarioId;
    }

    public String getTipoUsuario() {
        return this.tipoUsuario;
    }

    @Override
    public String toString() {
        return "remarcar aula " + this.aulaId + " para horario " + this.horarioId
                + " (" + this.tipoUsuario + " " + this.usuarioId + ")";
    }
}
