// Tarefa 2.3 — Tela de horários do professor.
// O professor monta a semana: clica num horário vazio para deixá-lo livre
// (com a modalidade escolhida) e clica de novo para remover.
import { useEffect, useState } from "react";
import { api } from "../api.js";
import { pegarUsuario } from "../sessao.js";
import { diaEMes, inicioDaSemana, nomeCurtoDoDia, paraTexto, somarDias } from "../datas.js";

const HORAS = ["07:00", "08:00", "09:00", "10:00", "11:00", "12:00", "13:00", "14:00", "15:00", "16:00", "17:00", "18:00", "19:00", "20:00", "21:00"];

// "14:00" -> "15:00" (cada horário tem 1 hora)
function maisUmaHora(hora) {
  const h = Number(hora.slice(0, 2)) + 1;
  return `${String(h).padStart(2, "0")}:00`;
}

export default function Horarios() {
  const usuario = pegarUsuario();
  const [segunda, setSegunda] = useState(inicioDaSemana(new Date()));
  const [horarios, setHorarios] = useState([]);
  const [modalidade, setModalidade] = useState("online");
  const [erro, setErro] = useState("");
  const [copiado, setCopiado] = useState(false);

  const dias = [0, 1, 2, 3, 4, 5, 6].map((i) => somarDias(segunda, i));
  const de = paraTexto(dias[0]);
  const ate = paraTexto(dias[6]);
  const link = `${window.location.origin}/#/agendamento/${usuario.id}`;

  async function carregar() {
    try {
      const resposta = await api.meusHorarios(de, ate);
      setHorarios(resposta.horarios);
    } catch (err) {
      setErro(err.message);
    }
  }

  useEffect(() => {
    carregar();
  }, [de]);

  // Clicou numa célula: cria o horário se estiver vazia, remove se já existir
  async function clicar(dia, hora, horario) {
    setErro("");
    try {
      if (horario) {
        await api.removerHorario(horario.id);
      } else {
        await api.criarHorario(dia, hora, maisUmaHora(hora), modalidade);
      }
      carregar();
    } catch (err) {
      setErro(err.message);
    }
  }

  async function copiarLink() {
    await navigator.clipboard.writeText(link);
    setCopiado(true);
    setTimeout(() => setCopiado(false), 2000);
  }

  const agora = new Date();
  const livres = horarios.filter((h) => !h.reservado).length;

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold">Meus horários</h1>
        <p className="mt-1 text-cinza">Clique em um horário vazio para deixá-lo livre. Clique de novo para remover.</p>
      </div>

      <div className="card space-y-2">
        <h2 className="text-lg font-semibold">Seu link de agendamento</h2>
        <p className="text-sm text-cinza">Mande este link para seus alunos agendarem.</p>
        <div className="flex gap-2">
          <input readOnly value={link} className="input min-w-0 flex-1 text-sm" onFocus={(e) => e.target.select()} />
          <button type="button" onClick={copiarLink} className="rounded-lg bg-texto px-4 py-2 text-sm font-semibold text-white hover:bg-zinc-700">
            {copiado ? "Copiado!" : "Copiar"}
          </button>
        </div>
      </div>

      <div className="flex flex-wrap items-center justify-between gap-4">
        <div className="flex items-center gap-2">
          <button type="button" onClick={() => setSegunda(somarDias(segunda, -7))} className="rounded-lg border border-borda bg-white px-3 py-2 hover:bg-zinc-100">←</button>
          <span className="text-center font-semibold sm:w-56">
            {diaEMes(dias[0])} a {diaEMes(dias[6])}
          </span>
          <button type="button" onClick={() => setSegunda(somarDias(segunda, 7))} className="rounded-lg border border-borda bg-white px-3 py-2 hover:bg-zinc-100">→</button>
        </div>

        <div className="flex items-center gap-3 text-sm">
          <span className="text-cinza">Novos horários:</span>
          <label className="flex items-center gap-1.5">
            <input type="radio" name="modalidade" checked={modalidade === "online"} onChange={() => setModalidade("online")} />
            Online
          </label>
          <label className="flex items-center gap-1.5">
            <input type="radio" name="modalidade" checked={modalidade === "presencial"} onChange={() => setModalidade("presencial")} />
            Presencial
          </label>
        </div>
      </div>

      {erro && <p className="rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-erro">{erro}</p>}

      <div className="card overflow-x-auto p-4">
        <table className="w-full min-w-[640px] table-fixed border-separate border-spacing-1 text-sm">
          <thead>
            <tr>
              <th className="w-14"></th>
              {dias.map((d) => (
                <th key={paraTexto(d)} className="pb-2 font-medium">
                  <span className="block text-xs text-cinza">{nomeCurtoDoDia(d)}</span>
                  {d.getDate()}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {HORAS.map((hora) => (
              <tr key={hora}>
                <td className="pr-2 text-right text-xs text-cinza">{hora}</td>
                {dias.map((d) => {
                  const dia = paraTexto(d);
                  const horario = horarios.find((h) => h.dia === dia && h.horaInicio === hora);
                  const inicio = new Date(`${dia}T${hora}`);
                  return (
                    <td key={dia}>
                      <Celula horario={horario} passou={inicio <= agora} onClick={() => clicar(dia, hora, horario)} />
                    </td>
                  );
                })}
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <div className="flex flex-wrap gap-4 text-sm text-cinza">
        <span>{livres} horários livres nesta semana</span>
        <span className="flex items-center gap-1.5"><span className="size-3 rounded bg-blue-100"></span> Online</span>
        <span className="flex items-center gap-1.5"><span className="size-3 rounded bg-emerald-100"></span> Presencial</span>
        <span className="flex items-center gap-1.5"><span className="size-3 rounded bg-texto"></span> Reservado</span>
      </div>
    </div>
  );
}

// Uma célula da tabela: vazia, livre (online/presencial) ou reservada
function Celula({ horario, passou, onClick }) {
  const base = "h-10 w-full rounded-md text-xs font-medium";

  if (horario?.reservado) {
    return (
      <div className={`${base} flex items-center justify-center bg-texto text-white`} title={`Reservado por ${horario.aluno}`}>
        Reservado
      </div>
    );
  }
  if (passou) {
    return <div className={`${base} flex items-center justify-center ${horario ? "bg-zinc-200 text-cinza" : "bg-zinc-50"}`}>{horario ? "Livre" : ""}</div>;
  }
  if (horario) {
    const online = horario.modalidade === "online";
    return (
      <button type="button" onClick={onClick} title="Clique para remover"
        className={`${base} ${online ? "bg-blue-100 text-blue-700 hover:bg-blue-200" : "bg-emerald-100 text-emerald-700 hover:bg-emerald-200"}`}>
        {online ? "Online" : "Presencial"}
      </button>
    );
  }
  return <button type="button" onClick={onClick} title="Clique para deixar livre" className={`${base} border border-dashed border-borda bg-white hover:bg-zinc-100`}></button>;
}
