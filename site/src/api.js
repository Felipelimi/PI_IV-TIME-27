// Único arquivo que fala com o servidor (tarefa 1.5).
// Todas as telas usam as funções daqui, nenhuma faz fetch direto.
import { pegarToken } from "./sessao.js";

const API_URL = import.meta.env.VITE_API_URL || "/api";

// Faz a chamada ao servidor e devolve o JSON da resposta.
// Se o servidor responder erro, lança um Error com a mensagem de { "erro": "..." }.
async function chamar(metodo, caminho, corpo) {
  const token = pegarToken();

  let resposta;
  try {
    resposta = await fetch(API_URL + caminho, {
      method: metodo,
      headers: {
        "Content-Type": "application/json",
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
      },
      body: corpo ? JSON.stringify(corpo) : undefined,
    });
  } catch {
    throw new Error("Não foi possível falar com o servidor. Confira se ele está rodando.");
  }

  const dados = resposta.status === 204 ? null : await resposta.json().catch(() => null);

  if (!resposta.ok) {
    const mensagem = dados?.erro || "Não foi possível falar com o servidor. Confira se ele está rodando.";
    const erro = new Error(mensagem);
    erro.status = resposta.status;
    throw erro;
  }
  return dados;
}

// Rotas que já existem no servidor (feature/cadastro-login)
export const api = {
  cadastrar: (nome, email, senha, tipo) => chamar("POST", "/cadastro", { nome, email, senha, tipo }),
  entrar: (email, senha) => chamar("POST", "/login", { email, senha }),
  eu: () => chamar("GET", "/me"),
};
