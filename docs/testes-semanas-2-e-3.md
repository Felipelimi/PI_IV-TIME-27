# Tarefa 3.4 — validação das entregas das semanas 2 e 3

## Resultado em 23/11/2026

Código validado: `main` no commit `4922cdc`, que inclui as telas 4.2 e 4.4.
Ambiente executado: Mac local, Java 25, Maven, MongoDB local, Node 24 e Chromium
151 pelo Playwright 1.62.1. Site em `http://127.0.0.1:5173`, API em
`http://localhost:8081`, banco isolado `easyagenda_s3_validacao`.

- Compilação de produção do site: aprovada.
- JUnit: 39 testes, zero falhas, zero erros, nenhum ignorado.
- Navegador com API real: nove cenários aprovados (dez testes no relatório Node,
  pois a suíte principal também é contada), zero falhas.
- Nenhuma exceção JavaScript nas sessões do navegador durante o roteiro.
- Nenhum defeito funcional identificado no escopo executado. Não foram abertas
  issues de defeito sem uma reprodução confirmada.

**Situação da tarefa: validação local concluída; validação publicada pendente.**
Este resultado não comprova o funcionamento de uma versão publicada.

## Roteiro e resultados

Os passos abaixo foram executados pelo teste em
[`site/tests/entregas-s2-s3.test.mjs`](../site/tests/entregas-s2-s3.test.mjs).
As sessões de professor e alunos são separadas, com cadastros pela interface.
Não há respostas de API simuladas ou tokens inseridos manualmente nas sessões.

| Caso | Passos | Resultado esperado | Local |
|---|---|---|---|
| 1 | Cadastrar professor pelo site | Login automático, token salvo e redirecionamento para Meus horários | Aprovado |
| 2 | Na semana seguinte, criar 10h online, 12h presencial e 14h online; remover 14h; recarregar e voltar à semana | Horários de 10h e 12h persistem com suas modalidades | Aprovado |
| 3 | Abrir o link do professor em outra sessão e cadastrar aluno | Retornar à agenda do mesmo professor, com token salvo | Aprovado |
| 4 | Selecionar 10h, cancelar a seleção, selecionar novamente e confirmar | Nenhuma reserva antes da confirmação; sucesso e remoção de 10h após confirmar | Aprovado |
| 5 | Em dois alunos, selecionar 12h antes de confirmar; confirmar um e depois o outro | Uma reserva; segundo aluno recebe mensagem de horário ocupado e lista atualizada; API responde 409 em nova tentativa | Aprovado |
| 6 | Recarregar a agenda do professor e voltar à semana | Duas células reservadas | Aprovado |
| 7 | Tentar consultar horários sem token e como aluno; tentar agendar como professor; aluno abrir /horarios | API responde 401/403; aluno não acessa tela exclusiva de professor | Aprovado |
| 8 | Sair, tentar login com senha incorreta e depois senha correta | Login inválido responde 401 e não salva token; login correto permite entrar | Aprovado |
| 9 | Cadastrar com senha curta; depois tentar e-mail já existente | Mensagem de senha mínima; e-mail duplicado responde 409; nenhum token salvo | Aprovado |

## Como reproduzir localmente

Na raiz, inicie o banco:

```bash
docker compose up -d mongo
```

Em um terminal, inicie a API com banco separado:

```bash
cd servidor
PORT=8081 MONGODB_DB=easyagenda_s3_validacao mvn compile exec:java
```

Em outro, prepare e inicie o site:

```bash
cd site
npm ci
npx playwright install chromium
VITE_API_URL=http://localhost:8081 npm run dev -- --host 127.0.0.1
```

Com site e API ativos, execute em outro terminal:

```bash
cd site
npm run test:e2e
npm run build
```

Para os testes Java, com o MongoDB local ativo:

```bash
cd servidor
mvn test
```

Cada execução de navegador cria contas com e-mails únicos em `example.com` e
horários na semana seguinte. Os dados permanecem no banco de validação para
inspeção. O teste não apaga dados existentes. Os testes JUnit usam seu próprio
banco `easyagenda_test`, que é limpo pelo código dos testes.

## Pendência: versão publicada

A [tarefa 2.4 (#9)](https://github.com/Felipelimi/PI_IV-TIME-27/issues/9) é
pré-requisito da [3.4 (#13)](https://github.com/Felipelimi/PI_IV-TIME-27/issues/13).
Não foi possível identificar uma URL real de teste:

- O README traz exemplos e instruções de publicação, não um endereço confirmado.
- A tarefa 2.4 não tem comentários com URLs.
- O repositório não tem homepage definida nem registros na API de deployments.
- As cinco execuções mais recentes de "Manter servidor acordado" consultadas
  estavam em `skipped`. Isso não confirma a inexistência de um servidor externo.
- O responsável nesta sessão também não soube informar o endereço.

Falta informar/publicar o site e a API da tarefa 2.4 e executar o mesmo roteiro
nesse ambiente. O comando abaixo permite reutilizar o teste; `E2E_API_URL` deve
ser a API configurada no site publicado, sem barra final:

```bash
cd site
E2E_SITE_URL=https://ENDERECO-DO-SITE E2E_API_URL=https://ENDERECO-DA-API npm run test:e2e
```

Registrar URL, versão publicada, data e resultados de cada caso. Abrir uma issue
por defeito reproduzido, contendo passos, esperado, observado e ambiente. Se não
houver erros graves, concluir a 3.4. Até essa execução, a issue deve continuar aberta.
