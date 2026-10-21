import { NavLink, useNavigate } from "react-router-dom";
import { pegarUsuario, sair } from "../sessao.js";

const linksProfessor = [
  { to: "/horarios", texto: "Horários" },
  { to: "/proximas-aulas", texto: "Próximas aulas" },
  { to: "/historico", texto: "Histórico" },
];

const linksAluno = [
  { to: "/agendamento", texto: "Agendamento" },
  { to: "/proximas-aulas", texto: "Próximas aulas" },
  { to: "/historico", texto: "Histórico" },
];

export default function Menu() {
  const navigate = useNavigate();
  const usuario = pegarUsuario();
  const links = usuario?.tipo === "professor" ? linksProfessor : linksAluno;

  function handleSair() {
    sair();
    navigate("/login");
  }

  return (
    <header className="border-b border-borda bg-white">
      <div className="mx-auto flex max-w-5xl flex-wrap items-center justify-between gap-4 px-4 py-3">
        <span className="text-lg font-bold">EasyAgenda</span>

        <nav className="flex flex-wrap gap-1">
          {links.map((link) => (
            <NavLink
              key={link.to}
              to={link.to}
              className={({ isActive }) =>
                `rounded-lg px-3 py-2 text-sm font-medium ${isActive ? "bg-zinc-100 text-texto" : "text-cinza hover:text-texto"}`
              }
            >
              {link.texto}
            </NavLink>
          ))}
        </nav>

        <div className="flex items-center gap-3 text-sm">
          <span className="text-cinza">{usuario?.nome}</span>
          <button onClick={handleSair} className="rounded-lg border border-borda px-3 py-2 font-medium hover:bg-zinc-100">
            Sair
          </button>
        </div>
      </div>
    </header>
  );
}
