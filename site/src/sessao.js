// Guarda quem está logado no navegador (tarefa 2.1).
// O token vai em toda chamada ao servidor; o usuário serve para mostrar o nome e o tipo.

export function salvarSessao(token, usuario) {
  localStorage.setItem("token", token);
  localStorage.setItem("usuario", JSON.stringify(usuario));
}

export function pegarToken() {
  return localStorage.getItem("token");
}

export function pegarUsuario() {
  const texto = localStorage.getItem("usuario");
  return texto ? JSON.parse(texto) : null;
}

export function sair() {
  localStorage.removeItem("token");
  localStorage.removeItem("usuario");
}

// Cada tipo de usuário tem a sua página inicial
export function paginaInicial(usuario) {
  return usuario?.tipo === "professor" ? "/horarios" : "/agendamento";
}
