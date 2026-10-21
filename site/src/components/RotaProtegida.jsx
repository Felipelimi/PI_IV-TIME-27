import { Navigate } from "react-router-dom";
import { pegarUsuario, paginaInicial } from "../sessao.js";

// Só deixa abrir a página quem está logado (e, se for o caso, do tipo certo).
export default function RotaProtegida({ tipo, children }) {
  const usuario = pegarUsuario();

  if (!usuario) {
    return <Navigate to="/login" replace />;
  }
  if (tipo && usuario.tipo !== tipo) {
    return <Navigate to={paginaInicial(usuario)} replace />;
  }
  return children;
}
