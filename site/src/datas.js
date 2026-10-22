// Funções de data usadas nas telas de horários e agendamento.

// Data no formato 2026-10-20 (é assim que o servidor guarda)
export function paraTexto(data) {
  const ano = data.getFullYear();
  const mes = String(data.getMonth() + 1).padStart(2, "0");
  const dia = String(data.getDate()).padStart(2, "0");
  return `${ano}-${mes}-${dia}`;
}

export function somarDias(data, dias) {
  const nova = new Date(data);
  nova.setDate(nova.getDate() + dias);
  return nova;
}

// Segunda-feira da semana da data
export function inicioDaSemana(data) {
  const segunda = new Date(data);
  segunda.setHours(0, 0, 0, 0);
  const diaDaSemana = (segunda.getDay() + 6) % 7; // segunda = 0 ... domingo = 6
  segunda.setDate(segunda.getDate() - diaDaSemana);
  return segunda;
}

// Ex.: "seg."
export function nomeCurtoDoDia(data) {
  return data.toLocaleDateString("pt-BR", { weekday: "short" });
}

// Ex.: "20 de out."
export function diaEMes(data) {
  return data.toLocaleDateString("pt-BR", { day: "numeric", month: "short" });
}
