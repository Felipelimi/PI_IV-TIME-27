import { useEffect, useRef, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { api } from "../api.js";
import { inicioDaSemana, paraTexto, somarDias } from "../datas.js";
import ResumoAula, { dataDaAula } from "../components/ResumoAula.jsx";

const secundario = "rounded-lg border border-borda px-4 py-2 text-sm font-semibold hover:bg-zinc-100 disabled:opacity-50";
export default function GerenciarAula() {
  const { aulaId } = useParams();
  const [aula, setAula] = useState(null);
  const [carregando, setCarregando] = useState(true);
  const [erro, setErro] = useState("");
  const [erroHorarios, setErroHorarios] = useState("");
  const [tentativa, setTentativa] = useState(0);
  const [acao, setAcao] = useState(null);
  const [semana, setSemana] = useState(() => inicioDaSemana(new Date()));
  const [horarios, setHorarios] = useState([]);
  const [carregandoHorarios, setCarregandoHorarios] = useState(false);
  const [selecionado, setSelecionado] = useState("");
  const [enviando, setEnviando] = useState(false);
  const trava = useRef(false);
  const [sucesso, setSucesso] = useState("");
  const de = paraTexto(semana);
  const ate = paraTexto(somarDias(semana, 6));
  useEffect(() => {
    let ativo = true;
    setCarregando(true); setErro(""); setSucesso(""); setAcao(null); setAula(null);
    api.proximasAulas().then(r => {
      if (ativo) setAula(r.aulas.find(a => a.id === aulaId) || null);
    }).catch(e => { if (ativo) setErro(e.message); })
      .finally(() => { if (ativo) setCarregando(false); });
    return () => { ativo = false; };
  }, [aulaId, tentativa]);
  useEffect(() => {
    if (acao !== "remarcar" || !aula) return;
    let ativo = true;
    setHorarios([]); setSelecionado(""); setErroHorarios(""); setCarregandoHorarios(true);
    api.horariosDoProfessor(aula.professorId, de, ate).then(r => {
      if (ativo) setHorarios(r.horarios);
    }).catch(e => { if (ativo) setErroHorarios(e.message); })
      .finally(() => { if (ativo) setCarregandoHorarios(false); });
    return () => { ativo = false; };
  }, [acao, aula, de, ate, tentativa]);
  async function confirmar(e) {
    e.preventDefault();
    if (trava.current || !acao || (acao === "remarcar" && (!selecionado || carregandoHorarios))) return;
    trava.current = true; setEnviando(true); setErro("");
    try {
      if (acao === "cancelar") await api.cancelarAula(aula.id);
      else await api.remarcarAula(aula.id, selecionado);
      setSucesso(acao === "cancelar" ? "Aula cancelada com sucesso. O horário foi liberado." : "Aula remarcada com sucesso. Confira o novo horário em Próximas aulas.");
      setAcao(null);
    } catch (e) {
      setErro(e.message);
      // Uma disputa de horário não deve deixar a opção antiga selecionada.
      if (acao === "remarcar" && [400, 404, 409].includes(e.status)) {
        setSelecionado("");
        try { const r = await api.horariosDoProfessor(aula.professorId, de, ate); setHorarios(r.horarios); }
        catch (falha) { setErroHorarios(falha.message); }
      }
    } finally { trava.current = false; setEnviando(false); }
  }
  function escolher(qual) { setAcao(qual); setErro(""); setSelecionado(""); }
  return <section className="space-y-6">
    <Link className="text-sm font-semibold underline" to="/proximas-aulas">Voltar às próximas aulas</Link>
    <h1 className="text-2xl font-bold">Cancelar ou remarcar aula</h1>
    {erro && <div role="alert" className="rounded-lg border border-red-200 bg-red-50 p-4 text-erro"><p>{erro}</p>{!aula && <button className="mt-2 underline" onClick={() => setTentativa(v => v + 1)}>Tentar novamente</button>}</div>}
    {carregando ? <p role="status">Carregando aula…</p> : sucesso ? <div role="status" className="card space-y-4"><p className="text-emerald-800">{sucesso}</p><Link className="font-semibold underline" to="/proximas-aulas">Ver próximas aulas</Link></div> : !aula ? !erro && <div className="card">Esta aula não está mais entre suas próximas aulas. Consulte o histórico ou volte à agenda.</div> : <>
      <div className="card"><ResumoAula aula={aula} /></div>
      <p className="rounded-lg border border-borda bg-white p-4 text-sm">Prazo mínimo para cancelar ou remarcar: <strong>{aula.prazoCancelamentoHoras} horas de antecedência</strong>. Pedidos após o prazo ou depois do início da aula serão recusados.</p>
      <div className="flex flex-wrap gap-3"><button type="button" className={secundario} disabled={enviando} onClick={() => escolher("cancelar")}>Cancelar aula</button><button type="button" className={secundario} disabled={enviando} onClick={() => escolher("remarcar")}>Remarcar aula</button></div>
      {acao && <form onSubmit={confirmar} className="card space-y-5">
        <h2 className="text-lg font-semibold">{acao === "cancelar" ? "Confirmar cancelamento" : "Escolher novo horário"}</h2>
        {acao === "cancelar" ? <p>Deseja cancelar esta aula? O horário ficará disponível para outros agendamentos.</p> : <>
          <p className="text-sm text-cinza">Escolha outro horário livre do mesmo professor.</p>
          <div className="flex flex-wrap items-center justify-between gap-3"><button type="button" className={secundario} disabled={enviando} onClick={() => { setSelecionado(""); setSemana(somarDias(semana, -7)); }}>Semana anterior</button><span className="text-sm">{dataDaAula(de)} a {dataDaAula(ate)}</span><button type="button" className={secundario} disabled={enviando} onClick={() => { setSelecionado(""); setSemana(somarDias(semana, 7)); }}>Próxima semana</button></div>
          {carregandoHorarios ? <p role="status">Carregando horários…</p> : erroHorarios ? <p role="alert" className="text-erro">{erroHorarios} <button type="button" className="underline" onClick={() => setAcao(null)}>Fechar e tentar novamente</button></p> : horarios.length === 0 ? <p>Nenhum horário livre nesta semana. Consulte outra semana.</p> : <fieldset disabled={enviando} className="grid gap-3 sm:grid-cols-2"><legend className="mb-3 font-medium">Horários disponíveis</legend>{horarios.map(h => <label key={h.id} className={`flex cursor-pointer items-start gap-3 rounded-lg border p-3 ${selecionado === h.id ? "border-texto bg-zinc-50" : "border-borda"}`}><input type="radio" name="horario" value={h.id} checked={selecionado === h.id} onChange={() => setSelecionado(h.id)} className="mt-1" /><span className="text-sm">{dataDaAula(h.dia)}<span className="mt-1 block">{h.horaInicio} – {h.horaFim} · {h.modalidade === "online" ? "Online" : "Presencial"}</span></span></label>)}</fieldset>}
        </>}
        <div className="flex flex-wrap gap-3"><button type="submit" className="rounded-lg bg-texto px-5 py-2.5 text-sm font-semibold text-white disabled:opacity-50" disabled={enviando || (acao === "remarcar" && (!selecionado || carregandoHorarios || !!erroHorarios))}>{enviando ? "Confirmando…" : acao === "cancelar" ? "Confirmar cancelamento" : "Confirmar remarcação"}</button><button type="button" className={secundario} disabled={enviando} onClick={() => setAcao(null)}>Voltar</button></div>
      </form>}
    </>}
  </section>;
}
