import { NavLink } from "react-router-dom";

const links = [
  { to: "/login", texto: "Login" },
  { to: "/horarios", texto: "Horários" },
  { to: "/agendamento", texto: "Agendamento" },
  { to: "/proximas-aulas", texto: "Próximas aulas" },
  { to: "/historico", texto: "Histórico" },
];

export default function Menu() {
  return (
    <header className="border-b border-borda bg-white">
      <div className="mx-auto flex max-w-5xl flex-wrap items-center gap-4 px-4 py-3">
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
      </div>
    </header>
  );
}
