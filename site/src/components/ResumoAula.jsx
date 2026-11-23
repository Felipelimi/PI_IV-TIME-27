import { pegarUsuario } from "../sessao.js";

export function dataDaAula(dia) {
  return new Date(`${dia}T12:00:00`).toLocaleDateString("pt-BR", {
    weekday: "short", day: "2-digit", month: "long", year: "numeric",
  });
}

export default function ResumoAula({ aula }) {
  const professor = pegarUsuario()?.tipo === "professor";
  const status = { agendada: "Agendada", realizada: "Realizada", cancelada: "Cancelada" };
  return <div className="space-y-2">
    <div className="flex flex-wrap items-center justify-between gap-2">
      <h2 className="text-lg font-semibold">{professor ? aula.alunoNome || "Aluno" : aula.professorNome || "Professor"}</h2>
      <span className={`rounded-full px-3 py-1 text-sm font-medium ${aula.status === "cancelada" ? "bg-red-50 text-red-700" : "bg-emerald-50 text-emerald-800"}`}>{status[aula.status] || aula.status}</span>
    </div>
    <p className="capitalize">{dataDaAula(aula.dia)}</p>
    <p className="text-sm text-cinza">{aula.horaInicio}{aula.horaFim ? ` – ${aula.horaFim}` : ""} · {aula.modalidade === "online" ? "Online" : "Presencial"}</p>
  </div>;
}
