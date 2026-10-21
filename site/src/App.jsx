import { Navigate, Route, Routes } from "react-router-dom";
import Menu from "./components/Menu.jsx";
import Login from "./pages/Login.jsx";
import Horarios from "./pages/Horarios.jsx";
import Agendamento from "./pages/Agendamento.jsx";
import ProximasAulas from "./pages/ProximasAulas.jsx";
import Historico from "./pages/Historico.jsx";

// Páginas da tarefa 1.5 (por enquanto vazias)
export default function App() {
  return (
    <div className="min-h-screen">
      <Menu />
      <main className="mx-auto max-w-5xl px-4 py-8">
        <Routes>
          <Route path="/" element={<Navigate to="/login" />} />
          <Route path="/login" element={<Login />} />
          <Route path="/horarios" element={<Horarios />} />
          <Route path="/agendamento" element={<Agendamento />} />
          <Route path="/proximas-aulas" element={<ProximasAulas />} />
          <Route path="/historico" element={<Historico />} />
          <Route path="*" element={<p>Página não encontrada.</p>} />
        </Routes>
      </main>
    </div>
  );
}
