import { useState } from "react";
import { Link, Navigate, useNavigate } from "react-router-dom";
import { api } from "../api.js";
import { paginaInicial, pegarUsuario, salvarSessao } from "../sessao.js";

export default function Login() {
  const navigate = useNavigate();
  const [email, setEmail] = useState("");
  const [senha, setSenha] = useState("");
  const [erro, setErro] = useState("");
  const [carregando, setCarregando] = useState(false);

  // Quem já está logado vai direto para a sua página
  const logado = pegarUsuario();
  if (logado) {
    return <Navigate to={paginaInicial(logado)} replace />;
  }

  async function handleSubmit(e) {
    e.preventDefault();
    setErro("");

    if (!email || !senha) {
      setErro("Preencha o e-mail e a senha.");
      return;
    }

    setCarregando(true);
    try {
      const resposta = await api.entrar(email, senha);
      salvarSessao(resposta.token, resposta.usuario);
      navigate(paginaInicial(resposta.usuario));
    } catch (err) {
      setErro(err.message);
    } finally {
      setCarregando(false);
    }
  }

  return (
    <div className="flex min-h-screen items-center justify-center px-4">
      <div className="card w-full max-w-sm">
        <p className="mb-6 text-lg font-bold">EasyAgenda</p>
        <h1 className="text-2xl font-bold">Entrar</h1>
        <p className="mb-6 mt-1 text-sm text-cinza">Use o e-mail e a senha da sua conta.</p>

        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <label htmlFor="email" className="label">E-mail</label>
            <input id="email" type="email" autoComplete="email" className="input" value={email} onChange={(e) => setEmail(e.target.value)} />
          </div>
          <div>
            <label htmlFor="senha" className="label">Senha</label>
            <input id="senha" type="password" autoComplete="current-password" className="input" value={senha} onChange={(e) => setSenha(e.target.value)} />
          </div>

          {erro && <p className="text-sm text-erro">{erro}</p>}

          <button type="submit" className="btn" disabled={carregando}>
            {carregando ? "Entrando..." : "Entrar"}
          </button>
        </form>

        <p className="mt-6 text-center text-sm text-cinza">
          Não tem conta? <Link to="/cadastro" className="font-semibold text-texto underline">Criar conta</Link>
        </p>
      </div>
    </div>
  );
}
