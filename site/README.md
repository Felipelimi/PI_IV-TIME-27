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

## Agendamento do aluno (tarefa 3.2)

Em **Horários**, o professor copia seu link de agendamento e envia ao aluno. O aluno abre o
link para consultar os horários livres; se precisar entrar ou criar conta, volta para a agenda
do mesmo professor após o login ou cadastro.

O aluno seleciona um horário e clica em **Confirmar agendamento**. Pode usar **Cancelar seleção**
antes de confirmar. Quando a reserva dá certo, aparece a mensagem de sucesso e o horário sai
da lista. Se outro aluno reservar primeiro, o servidor responde `409`: a lista é atualizada e
o aviso de que o horário acabou de ser ocupado continua visível para o aluno escolher outro.

Para testar com banco, servidor e site rodando:

1. Entre como professor, abra pelo menos dois horários futuros e copie o link de agendamento.
2. Abra o link em uma sessão sem login e entre como aluno (ou crie uma conta de aluno).
   Confira se voltou para a agenda do professor. Selecione um horário, cancele a seleção,
   selecione novamente e confirme. Confira a mensagem de sucesso e a remoção do horário.
3. Para testar a disputa, use dois alunos diferentes em sessões separadas (por exemplo, uma
   janela normal e outra privada). Abra o mesmo link e selecione o mesmo horário livre nas duas
   sessões antes de confirmar. Confirme em uma e depois na outra: a primeira deve mostrar
   sucesso; a segunda deve manter o aviso de horário ocupado após atualizar a lista.

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
