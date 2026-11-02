// Tarefa 3.2 — Tela de agendamento do aluno.
// O aluno abre o link do professor, escolhe um horário livre e confirma.
import { useCallback, useEffect, useState } from "react";
import { useParams } from "react-router-dom";
import { api } from "../api.js";
import { pegarUsuario } from "../sessao.js";
import { diaEMes, inicioDaSemana, nomeCurtoDoDia, paraTexto, somarDias } from "../datas.js";

export default function Agendamento() {
  // O professorId vem do link que o professor copia na tela de horários.
  const { professorId: professorDaUrl } = useParams();
  const usuario = pegarUsuario();
  const professorId = professorDaUrl || usuario?.professorId;
  const [segunda, setSegunda] = useState(() => inicioDaSemana(new Date()));
  const [dados, setDados] = useState(null);
  const [selecionado, setSelecionado] = useState(null);
  const [carregando, setCarregando] = useState(false);
  const [confirmando, setConfirmando] = useState(false);
  const [erro, setErro] = useState("");
  const [aviso, setAviso] = useState("");

  const dias = [0, 1, 2, 3, 4, 5, 6].map((i) => somarDias(segunda, i));
  const de = paraTexto(dias[0]);
  const ate = paraTexto(dias[6]);
  const carregar = useCallback(async () => {
    if (!professorId) return;
    setCarregando(true);
    setErro("");
    try {
      setDados(await api.horariosDoProfessor(professorId, de, ate));
    } catch (e) {
      setErro(e.message);
    } finally {
      setCarregando(false);
    }
  }, [professorId, de, ate]);

  useEffect(() => { carregar(); }, [carregar]);

  // Confirma a escolha. Se alguém pegou o horário antes, atualiza a lista.
  async function confirmar() {
    if (!selecionado) return;
    setConfirmando(true);
    setErro("");
    setAviso("");
    try {
      await api.agendarHorario(selecionado.id);
      setAviso("Aula agendada com sucesso!");
      setSelecionado(null);
      await carregar();
    } catch (e) {
      if (e.status === 409) {
        setErro("Esse horário acabou de ser ocupado. Escolha outro horário disponível.");
        setSelecionado(null);
        await carregar();
      } else if (e.status === 404 || e.status === 405) {
        setErro("A confirmação de agendamento ainda não está disponível no servidor. A lista de horários está atualizada; tente novamente quando a tarefa 3.1 estiver pronta.");
      } else {
        setErro(e.message);
      }
    } finally {
      setConfirmando(false);
    }
  }

  // Separa os horários para mostrar cada dia da semana em sua própria lista.
  const horarios = dados?.horarios ?? [];
  const porDia = (dia) => horarios.filter((h) => h.dia === paraTexto(dia)).sort((a, b) => a.horaInicio.localeCompare(b.horaInicio));

  return (
    <section className="space-y-6">
      <header>
        <h1 className="text-2xl font-bold">Agendar aula</h1>
        <p className="mt-1 text-cinza">Escolha um horário livre com seu professor.</p>
      </header>

      {!professorId ? (
        <div className="card space-y-2">
          <h2 className="font-semibold">Acesse o link do professor</h2>
          <p className="text-sm text-cinza">Peça ao professor o link pessoal de agendamento e abra-o para consultar os horários disponíveis.</p>
        </div>
      ) : <>
        {dados?.professor?.nome && <p className="text-lg font-semibold">Horários de {dados.professor.nome}</p>}
        <div className="flex items-center justify-between gap-3">
          <button type="button" onClick={() => { setSelecionado(null); setSegunda(somarDias(segunda, -7)); }} className="rounded-lg border border-borda bg-white px-4 py-2 hover:bg-zinc-100">← Semana anterior</button>
          <span className="text-center font-medium">{diaEMes(dias[0])} a {diaEMes(dias[6])}</span>
          <button type="button" onClick={() => { setSelecionado(null); setSegunda(somarDias(segunda, 7)); }} className="rounded-lg border border-borda bg-white px-4 py-2 hover:bg-zinc-100">Próxima semana →</button>
        </div>

        {erro && <p role="alert" className="rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-erro">{erro}</p>}
        {aviso && <p role="status" className="rounded-lg border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm text-emerald-800">{aviso}</p>}
        {carregando ? <p role="status" className="py-8 text-center text-cinza">Carregando horários…</p> : erro && !dados ? null : horarios.length === 0 ? (
          <div className="card text-center text-cinza">Não há horários livres nesta semana. Tente outra semana.</div>
        ) : (
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
            {dias.map((dia) => {
              const lista = porDia(dia);
              return <section key={paraTexto(dia)} className="card space-y-3">
                <h2 className="font-semibold capitalize">{nomeCurtoDoDia(dia)} · {diaEMes(dia)}</h2>
                {lista.length ? <div className="grid grid-cols-2 gap-2">{lista.map((h) => {
                  const ativo = selecionado?.id === h.id;
                  return <button key={h.id} type="button" aria-pressed={ativo} onClick={() => { setSelecionado(h); setErro(""); setAviso(""); }} className={`rounded-lg border px-3 py-2 text-sm font-medium ${ativo ? "border-texto bg-texto text-white" : "border-borda hover:border-texto"}`}>
                    {h.horaInicio}{h.horaFim ? `–${h.horaFim}` : ""}<span className="mt-0.5 block text-xs font-normal">{h.modalidade === "online" ? "Online" : "Presencial"}</span>
                  </button>;
                })}</div> : <p className="text-sm text-cinza">Sem horários livres.</p>}
              </section>;
            })}
          </div>
        )}

        {selecionado && <div className="card flex flex-col items-start justify-between gap-4 sm:flex-row sm:items-center">
          <p>Confirmar {selecionado.dia}, às {selecionado.horaInicio} ({selecionado.modalidade})?</p>
          <button type="button" onClick={confirmar} disabled={confirmando} className="rounded-lg bg-texto px-5 py-2.5 text-sm font-semibold text-white hover:bg-zinc-700 disabled:opacity-60">{confirmando ? "Confirmando…" : "Confirmar agendamento"}</button>
        </div>}
      </>}
    </section>
  );
}
