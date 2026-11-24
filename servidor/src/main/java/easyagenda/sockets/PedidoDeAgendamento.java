package easyagenda.sockets;

/** O aluno quer marcar uma aula num horário livre (tarefa 3.1). */
public class PedidoDeAgendamento extends Comunicado {

    private static final long serialVersionUID = 1L;

    private final String horarioId; // pode vir null: o servidor responde o erro
    private final String alunoId;

    public PedidoDeAgendamento(String horarioId, String alunoId) {
        this.horarioId = horarioId;
        this.alunoId = alunoId;
    }

    public String getHorarioId() {
        return this.horarioId;
    }

    public String getAlunoId() {
        return this.alunoId;
    }

    @Override
    public String toString() {
        return "agendar horario " + this.horarioId + " para aluno " + this.alunoId;
    }
}
