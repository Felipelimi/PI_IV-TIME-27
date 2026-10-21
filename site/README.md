# EasyAgenda – Site

Site em **React + Tailwind** (Vite).

## Como rodar

Precisa do Node.js 20 ou mais novo.

```bash
cd site
npm install
npm run dev
```

Abra http://localhost:5173.

## Estrutura

```
src/
  main.jsx            liga o React e o roteador
  App.jsx             lista de páginas
  api.js              único arquivo que chama o servidor
  components/Menu.jsx menu de navegação
  pages/              Login, Horarios, Agendamento, ProximasAulas, Historico
```

O endereço do servidor fica em `VITE_API_URL` (padrão `http://localhost:8080`). Para mudar,
copie `.env.example` para `.env.local`.
