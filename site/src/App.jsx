import { Navigate, Route, Routes } from "react-router-dom";
import Menu from "./components/Menu.jsx";
import RotaProtegida from "./components/RotaProtegida.jsx";
import Login from "./pages/Login.jsx";
import Cadastro from "./pages/Cadastro.jsx";
import Horarios from "./pages/Horarios.jsx";
import Agendamento from "./pages/Agendamento.jsx";
import ProximasAulas from "./pages/ProximasAulas.jsx";
import Historico from "./pages/Historico.jsx";

// Páginas que aparecem com o menu em cima (só para quem está logado)
function ComMenu({ children }) {
  return (
    <div className="min-h-screen">
      <Menu />
      <main className="mx-auto max-w-5xl px-4 py-8">{children}</main>
    </div>
  );
}

export default function App() {
  return (
    <Routes>
      <Route path="/" element={<Navigate to="/login" />} />
      <Route path="/login" element={<Login />} />
      <Route path="/cadastro" element={<Cadastro />} />

      <Route path="/horarios" element={<RotaProtegida tipo="professor"><ComMenu><Horarios /></ComMenu></RotaProtegida>} />
      <Route path="/agendamento" element={<RotaProtegida tipo="aluno"><ComMenu><Agendamento /></ComMenu></RotaProtegida>} />
      <Route path="/proximas-aulas" element={<RotaProtegida><ComMenu><ProximasAulas /></ComMenu></RotaProtegida>} />
      <Route path="/historico" element={<RotaProtegida><ComMenu><Historico /></ComMenu></RotaProtegida>} />

      <Route path="*" element={<p className="p-8">Página não encontrada.</p>} />
    </Routes>
  );
}
