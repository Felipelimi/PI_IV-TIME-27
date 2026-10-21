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

## Login e cadastro (tarefa 2.1)

O site usa o servidor Java para criar conta e entrar. Antes de abrir o site, suba o banco e o
servidor (na raiz do repositório):

```bash
docker compose up -d --build
```

O Vite repassa as chamadas de `/api/...` para `http://localhost:8080` (veja `vite.config.js`).
Depois do login, o professor vai para **Horários** e o aluno para **Agendamento**.

## Estrutura

```
src/
  main.jsx            liga o React e o roteador
  App.jsx             lista de páginas
  api.js              único arquivo que chama o servidor
  sessao.js           guarda o token e o usuário logado
  components/         Menu e RotaProtegida
  pages/              Login, Cadastro, Horarios, Agendamento, ProximasAulas, Historico
```

O endereço do servidor fica em `VITE_API_URL` (padrão `/api`). Para mudar, copie
`.env.example` para `.env.local`.
