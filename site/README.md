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

## Próximas aulas, histórico e alterações (tarefas 4.2 e 4.4)

No menu **Próximas aulas**, aluno e professor consultam suas próprias aulas,
com nome do outro participante, data, horário, modalidade e status. **Histórico**
mostra aulas anteriores e canceladas. As telas consultam a API real e oferecem
estados de carregamento, lista vazia, erro e nova tentativa.

Em **Cancelar ou remarcar**, a tela exibe o prazo do professor. Cancelamento
exige confirmação; remarcação permite navegar por semanas, escolher um horário
livre do mesmo professor e confirmar. A seleção não envia o pedido sozinha.
A resposta do servidor aparece quando o pedido é recusado. Após sucesso,
a página oferece acesso à lista atualizada de próximas aulas.

O servidor permite alterar uma aula apenas ao aluno ou professor participante.
Ambos respeitam o prazo mínimo e a regra que impede alterar uma aula já iniciada.
Somente alunos podem fazer um agendamento inicial.

### Conferência manual

1. Cadastre um professor e um aluno em sessões separadas; crie pelo menos três
   horários futuros com mais de 24 horas de antecedência e agende dois como aluno.
2. Abra Próximas aulas em ambas as sessões: cada usuário deve ver as mesmas aulas
   com o nome do outro participante. Um terceiro usuário não deve vê-las.
3. Abra Cancelar ou remarcar. Confira o prazo, selecione Remarcar, navegue até a
   semana dos horários, escolha um horário livre e confirme. Confira a nova data
   em Próximas aulas e a antiga aula cancelada no Histórico.
4. Faça uma alteração como professor e outra como aluno. Confira os resultados
   nas duas sessões. Abrir uma aula de terceiros pelo endereço deve ser recusado.
5. Agende uma aula que começa em menos de 24 horas. Tente cancelar e remarcar:
   a mensagem de recusa deve aparecer e a aula deve continuar agendada.
6. Tente remarcar para um horário que outro aluno acabou de reservar: a aula
   antiga deve continuar agendada e a lista de horários deve ser atualizada.
7. Teste sem aulas, sem horários na semana e com o servidor desligado; confira
   mensagens de lista vazia e erro, e use Tentar novamente após religar o servidor.
8. Confira as telas em uma janela estreita, incluindo a seleção de horários.

Validação automatizada: `npm run build` no site e `mvn test` no servidor
(39 testes, incluindo permissões do professor, prazo, cancelamento e remarcação).

Verificação adicional com Playwright e API real em banco local separado:
listagem como aluno e professor, cancelamento pelo aluno e pelo professor,
remarcação pelo aluno, histórico atualizado, recusa por prazo e layout de celular
(390 px, sem transbordamento horizontal). Nenhum erro de JavaScript observado.
