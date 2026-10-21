import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { api } from "../api.js";
import { paginaInicial, salvarSessao } from "../sessao.js";

export default function Cadastro() {
  const navigate = useNavigate();
  const [form, setForm] = useState({ nome: "", email: "", senha: "", tipo: "professor" });
  const [erro, setErro] = useState("");
  const [carregando, setCarregando] = useState(false);

  function mudar(e) {
    setForm({ ...form, [e.target.name]: e.target.value });
  }

  async function handleSubmit(e) {
    e.preventDefault();
    setErro("");

    if (!form.nome || !form.email || !form.senha) {
      setErro("Preencha todos os campos.");
      return;
    }
    if (form.senha.length < 6) {
      setErro("A senha precisa ter pelo menos 6 caracteres.");
      return;
    }

    setCarregando(true);
    try {
      await api.cadastrar(form.nome, form.email, form.senha, form.tipo);
      // Depois de criar a conta, já entra direto
      const resposta = await api.entrar(form.email, form.senha);
      salvarSessao(resposta.token, resposta.usuario);
      navigate(paginaInicial(resposta.usuario));
    } catch (err) {
      setErro(err.message);
    } finally {
      setCarregando(false);
    }
  }

  // Estilo do botão de escolher o tipo de conta
  const estiloTipo = (valor) =>
    `flex-1 cursor-pointer rounded-lg border px-3 py-2.5 text-center text-sm font-medium ${
      form.tipo === valor ? "border-texto bg-texto text-white" : "border-borda bg-white hover:bg-zinc-50"
    }`;

  return (
    <div className="flex min-h-screen items-center justify-center px-4 py-10">
      <div className="card w-full max-w-sm">
        <p className="mb-6 text-lg font-bold">EasyAgenda</p>
        <h1 className="text-2xl font-bold">Criar conta</h1>
        <p className="mb-6 mt-1 text-sm text-cinza">Escolha se você é professor ou aluno.</p>

        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <p className="label">Tipo de conta</p>
            <div className="flex gap-2">
              <label className={estiloTipo("professor")}>
                <input type="radio" name="tipo" value="professor" checked={form.tipo === "professor"} onChange={mudar} className="sr-only" />
                Professor
              </label>
              <label className={estiloTipo("aluno")}>
                <input type="radio" name="tipo" value="aluno" checked={form.tipo === "aluno"} onChange={mudar} className="sr-only" />
                Aluno
              </label>
            </div>
          </div>
          <div>
            <label htmlFor="nome" className="label">Nome</label>
            <input id="nome" name="nome" type="text" autoComplete="name" className="input" value={form.nome} onChange={mudar} />
          </div>
          <div>
            <label htmlFor="email" className="label">E-mail</label>
            <input id="email" name="email" type="email" autoComplete="email" className="input" value={form.email} onChange={mudar} />
          </div>
          <div>
            <label htmlFor="senha" className="label">Senha</label>
            <input id="senha" name="senha" type="password" autoComplete="new-password" placeholder="Mínimo de 6 caracteres" className="input" value={form.senha} onChange={mudar} />
          </div>

          {erro && <p className="text-sm text-erro">{erro}</p>}

          <button type="submit" className="btn" disabled={carregando}>
            {carregando ? "Criando..." : "Criar conta"}
          </button>
        </form>

        <p className="mt-6 text-center text-sm text-cinza">
          Já tem conta? <Link to="/login" className="font-semibold text-texto underline">Entrar</Link>
        </p>
      </div>
    </div>
  );
}
