import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { api } from "../api.js";
import ResumoAula from "./ResumoAula.jsx";

export default function ListaAulas({ historico = false }) {
  const [aulas, setAulas] = useState([]);
  const [erro, setErro] = useState("");
  const [carregando, setCarregando] = useState(true);
  const [tentativa, setTentativa] = useState(0);
  useEffect(() => {
    let ativo = true;
    setCarregando(true);
    setErro("");
    (historico ? api.historicoAulas() : api.proximasAulas())
      .then(r => { if (ativo) setAulas(r.aulas); })
      .catch(e => { if (ativo) setErro(e.message); })
      .finally(() => { if (ativo) setCarregando(false); });
    return () => { ativo = false; };
  }, [historico, tentativa]);
  return <section className="space-y-6">
    <header><h1 className="text-2xl font-bold">{historico ? "Histórico" : "Próximas aulas"}</h1>
      <p className="mt-1 text-cinza">{historico ? "Consulte suas aulas anteriores e canceladas." : "Confira suas aulas agendadas e gerencie os próximos encontros."}</p>
    </header>
    {carregando ? <p role="status" className="card text-cinza">Carregando aulas…</p> : erro ? <div role="alert" className="card space-y-3 text-erro"><p>{erro}</p><button type="button" className="font-semibold underline" onClick={() => setTentativa(v => v + 1)}>Tentar novamente</button></div> : aulas.length === 0 ? <div className="card text-cinza">{historico ? "Seu histórico de aulas está vazio." : "Você ainda não tem aulas agendadas."}</div> : <ul className="grid gap-4 sm:grid-cols-2">{aulas.map(aula => <li key={aula.id} className="card space-y-4">
      <ResumoAula aula={aula} />
      {!historico && <Link className="inline-block rounded-lg border border-borda px-4 py-2 text-sm font-semibold hover:bg-zinc-100" to={`/aulas/${aula.id}/gerenciar`}>Cancelar ou remarcar</Link>}
    </li>)}</ul>}
  </section>;
}
