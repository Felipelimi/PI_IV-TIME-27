// Único arquivo que fala com o servidor (tarefa 1.5).
// Todas as telas usam as funções daqui, nenhuma faz fetch direto.

const API_URL = import.meta.env.VITE_API_URL || "http://localhost:8080";

// Faz a chamada ao servidor e devolve o JSON da resposta.
// Se o servidor responder erro, lança um Error com a mensagem de { "erro": "..." }.
async function chamar(metodo, caminho, corpo) {
  const token = localStorage.getItem("token");

  const resposta = await fetch(API_URL + caminho, {
    method: metodo,
    headers: {
      "Content-Type": "application/json",
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: corpo ? JSON.stringify(corpo) : undefined,
  });

  const dados = resposta.status === 204 ? null : await resposta.json().catch(() => null);

  if (!resposta.ok) {
    const erro = new Error(dados?.erro || "Erro ao falar com o servidor");
    erro.status = resposta.status;
    throw erro;
  }
  return dados;
}

// Rotas que já existem no servidor (feature/cadastro-login)
export const api = {
  ping: () => fetch(API_URL + "/ping").then((r) => r.text()),
  cadastrar: (nome, email, senha, tipo) => chamar("POST", "/cadastro", { nome, email, senha, tipo }),
  entrar: (email, senha) => chamar("POST", "/login", { email, senha }),
  eu: () => chamar("GET", "/me"),
};
