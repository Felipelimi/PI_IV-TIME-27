// Tarefa 3.2 — Tela de agendamento do aluno.
// O aluno abre o link do professor, escolhe um horário livre e confirma.
import { useEffect, useRef, useState } from "react";
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
  const enviando = useRef(false);
  const [atualizacao, setAtualizacao] = useState(0);
  const [erroCarregamento, setErroCarregamento] = useState("");
  const [erro, setErro] = useState("");
  const [aviso, setAviso] = useState("");

  const dias = [0, 1, 2, 3, 4, 5, 6].map((i) => somarDias(segunda, i));
  const de = paraTexto(dias[0]);
  const ate = paraTexto(dias[6]);
  useEffect(() => {
    // Uma resposta antiga não pode substituir a semana ou o professor atual.
    let ativo = true;
    setDados(null);
    setSelecionado(null);
    setErroCarregamento("");
    if (!professorId) return;
    setCarregando(true);

    async function carregar() {
      try {
        const resposta = await api.horariosDoProfessor(professorId, de, ate);
        if (ativo) setDados(resposta);
      } catch (e) {
        if (ativo) setErroCarregamento(e.message);
      } finally {
        if (ativo) setCarregando(false);
      }
    }
    carregar();
    return () => { ativo = false; };
  }, [professorId, de, ate, atualizacao]);

  function atualizarHorarios() {
    setSelecionado(null);
    setCarregando(true);
    setAtualizacao((valor) => valor + 1);
  }

  function mudarSemana(dias) {
    setSelecionado(null);
    setErro("");
    setAviso("");
    setCarregando(true);
    setSegunda((atual) => somarDias(atual, dias));
  }

  // Confirma a escolha. Se alguém pegou o horário antes, atualiza a lista.
  async function confirmar(e) {
    e.preventDefault();
    if (!selecionado || carregando || enviando.current) return;
    enviando.current = true;
    setConfirmando(true);
    setErro("");
    setAviso("");
    try {
      await api.agendarHorario(selecionado.id);
      setAviso("Aula agendada com sucesso!");
      atualizarHorarios();
    } catch (e) {
      if (e.status === 409) {
        setErro("Esse horário acabou de ser ocupado. Escolha outro horário disponível.");
        atualizarHorarios();
      } else if (e.status === 404) {
        setErro("Esse horário não está mais disponível. Escolha outro horário na lista atualizada.");
        atualizarHorarios();
      } else if (e.status === 400) {
        setErro(e.message);
        atualizarHorarios();
      } else {
        setErro(e.message);
      }
    } finally {
      enviando.current = false;
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
          <button type="button" onClick={() => mudarSemana(-7)} disabled={confirmando} className="rounded-lg border border-borda bg-white px-4 py-2 hover:bg-zinc-100 disabled:opacity-60">← Semana anterior</button>
          <span className="text-center font-medium">{diaEMes(dias[0])} a {diaEMes(dias[6])}</span>
          <button type="button" onClick={() => mudarSemana(7)} disabled={confirmando} className="rounded-lg border border-borda bg-white px-4 py-2 hover:bg-zinc-100 disabled:opacity-60">Próxima semana →</button>
        </div>

        {erro && <p role="alert" className="rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-erro">{erro}</p>}
        {aviso && <p role="status" className="rounded-lg border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm text-emerald-800">{aviso}</p>}
        {erroCarregamento && <div role="alert" className="space-y-2 rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-erro">
          <p>{erroCarregamento}</p>
          <button type="button" onClick={atualizarHorarios} disabled={carregando} className="font-semibold underline disabled:opacity-60">Tentar novamente</button>
        </div>}
        {carregando ? <p role="status" className="py-8 text-center text-cinza">Carregando horários…</p> : !dados ? null : horarios.length === 0 ? (
          <div className="card text-center text-cinza">Não há horários livres nesta semana. Tente outra semana.</div>
        ) : (
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
            {dias.map((dia) => {
              const lista = porDia(dia);
              return <section key={paraTexto(dia)} className="card space-y-3">
                <h2 className="font-semibold capitalize">{nomeCurtoDoDia(dia)} · {diaEMes(dia)}</h2>
                {lista.length ? <div className="grid grid-cols-2 gap-2">{lista.map((h) => {
                  const ativo = selecionado?.id === h.id;
                  return <button key={h.id} type="button" aria-pressed={ativo} disabled={confirmando} onClick={() => { setSelecionado(h); setErro(""); setAviso(""); }} className={`rounded-lg border px-3 py-2 text-sm font-medium disabled:opacity-60 ${ativo ? "border-texto bg-texto text-white" : "border-borda hover:border-texto"}`}>
                    {h.horaInicio}{h.horaFim ? `–${h.horaFim}` : ""}<span className="mt-0.5 block text-xs font-normal">{h.modalidade === "online" ? "Online" : "Presencial"}</span>
                  </button>;
                })}</div> : <p className="text-sm text-cinza">Sem horários livres.</p>}
              </section>;
            })}
          </div>
        )}

        {selecionado && <form onSubmit={confirmar} className="card space-y-4">
          <p>Confirmar {selecionado.dia}, às {selecionado.horaInicio} ({selecionado.modalidade})?</p>
          <div className="flex flex-wrap gap-3">
            <button type="submit" disabled={confirmando || carregando} className="rounded-lg bg-texto px-5 py-2.5 text-sm font-semibold text-white hover:bg-zinc-700 disabled:opacity-60">{confirmando ? "Confirmando…" : "Confirmar agendamento"}</button>
            <button type="button" onClick={() => setSelecionado(null)} disabled={confirmando} className="rounded-lg border border-borda px-5 py-2.5 text-sm font-semibold hover:bg-zinc-100 disabled:opacity-60">Cancelar seleção</button>
          </div>
        </form>}
      </>}
    </section>
  );
}
