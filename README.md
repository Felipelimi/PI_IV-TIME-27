# EasyAgenda – PI_IV-TIME-27

Servidor do EasyAgenda em **Java 25 LTS**, usando:

- `HttpServer` do próprio JDK para as rotas HTTP
- Driver oficial do **MongoDB** (`mongodb-driver-sync`)
- **JUnit 5** para os testes
- **Maven** para build e dependências
- **Docker Compose** (opcional) para subir servidor e banco com um comando

## Estrutura

```
docker-compose.yml          # sobe o MongoDB local e o servidor
servidor/
  pom.xml                   # dependências e build (Maven)
  Dockerfile                # imagem do servidor
  mongo-init/
    init.js                 # cria coleções, validações e índices no Mongo local (igual ao Atlas)
  src/main/java/easyagenda/
    Main.java               # ponto de entrada: conecta no banco e sobe o servidor
    Database.java           # conexão com o MongoDB
    Server.java             # servidor HTTP e rotas
    lembretes/              # lembrete por e-mail das aulas (rotina automática + envio SMTP)
  src/test/java/easyagenda/
    PingTest.java           # teste modelo
```

## Banco de dados: local x Atlas

O servidor precisa de um MongoDB para iniciar. Se não conseguir conectar, ele encerra logo de
cara. Existem duas opções de banco:

| | Banco local (Docker) | Atlas |
|---|---|---|
| Onde fica | Num container na sua máquina | Na nuvem |
| Quem usa | Só você: cada integrante tem o seu | O time todo, compartilhado |
| Para que serve | Desenvolver e testar à vontade sem afetar ninguém | Dados compartilhados do projeto |
| Dados | Começa vazio | Os que o time já cadastrou |

Os dois têm a **mesma estrutura**: as coleções `usuarios`, `horarios_livres` e `aulas`, com as
mesmas validações (`$jsonSchema`) e índices únicos. No banco local, isso é criado pelo
[servidor/mongo-init/init.js](servidor/mongo-init/init.js) na primeira vez que o container sobe.

> **Mudou a estrutura do banco no Atlas?** Atualize também o `init.js` e avise o time. Cada um
> precisa recriar o banco local para aplicar a mudança (isso **apaga os dados locais**):
>
> ```bash
> docker compose down -v && docker compose up --build
> ```

A escolha do banco é feita pela variável `MONGODB_URI` (veja [Configuração](#configuração)).

## Como rodar

Há duas formas. As duas rodam o servidor na sua máquina em http://localhost:8080; a diferença é
se o Java roda dentro do Docker ou direto no seu computador.

### Opção 1: tudo com Docker Compose (mais simples)

**Pré-requisito:** [Docker](https://docs.docker.com/get-docker/) (no Windows/macOS, o Docker
Desktop). Não precisa instalar Java, Maven nem MongoDB.

Na raiz do repositório:

```bash
docker compose up --build
```

Isso sobe o MongoDB 7 local (porta `27017`, já com a estrutura do banco) e o servidor (porta
`8080`). O servidor espera o banco ficar pronto antes de iniciar.

Comandos úteis:

```bash
docker compose up -d --build     # sobe em segundo plano
docker compose logs -f servidor  # acompanha os logs do servidor
docker compose down              # para tudo (mantém os dados)
docker compose down -v           # para tudo e apaga os dados do banco local
```

> Alterou o código Java? Rode `docker compose up --build` de novo para recompilar a imagem.

**Usar o Atlas em vez do banco local:** crie um arquivo `.env` na raiz do repositório (ele já está
no `.gitignore`, então suas credenciais não vão para o git):

```env
MONGODB_URI=mongodb+srv://USUARIO:SENHA@cluster.xxxxx.mongodb.net
```

### Opção 2: sem Docker (Java + Maven)

Melhor para o dia a dia: recompila mais rápido e permite depurar com breakpoints na IDE.

**Pré-requisitos:**

- JDK 25 ou mais novo (confira com `java -version`)
- Maven 3.8+ (confira com `mvn -version`)
- Um MongoDB disponível: o banco local do Docker **ou** o Atlas

**1. Escolha o banco**

- **Banco local:** suba só o container do Mongo. Não precisa configurar nada, porque o padrão do
  servidor já é `localhost:27017`:

  ```bash
  docker compose up -d mongo
  ```

- **Atlas:** defina a variável `MONGODB_URI` no terminal onde vai rodar o servidor:

  ```bash
  # Linux/macOS
  export MONGODB_URI="mongodb+srv://USUARIO:SENHA@cluster.xxxxx.mongodb.net"
  ```

  ```powershell
  # Windows (PowerShell)
  $env:MONGODB_URI="mongodb+srv://USUARIO:SENHA@cluster.xxxxx.mongodb.net"
  ```

  > O arquivo `.env` só é lido pelo Docker Compose; o Maven não o lê. Nesta opção, use a
  > variável de ambiente.

**2. Rode o servidor**

```bash
cd servidor
mvn compile exec:java
```

Quando aparecer `Servidor rodando em http://localhost:8080`, ele está no ar.

## Testando se está no ar

Abra http://localhost:8080/ping no navegador ou rode:

```bash
curl http://localhost:8080/ping
```

A resposta deve ser `ok`.

## Configuração

Variáveis de ambiente (todas opcionais):

| Variável      | Padrão                      | Para que serve                    |
|---------------|-----------------------------|-----------------------------------|
| `PORT`        | `8080`                      | Porta do servidor                 |
| `MONGODB_URI` | `mongodb://localhost:27017` | Endereço do MongoDB (local/Atlas) |
| `MONGODB_DB`  | `easyagenda`                | Nome do banco                     |

- **Opção 1 (Docker):** coloque as variáveis no arquivo `.env` da raiz.
- **Opção 2 (sem Docker):** defina-as no terminal (`export` / `$env:`).

### Lembrete por e-mail (tarefa 3.3)

O servidor confere a cada 10 minutos as aulas agendadas que começam nas próximas 24 horas e
manda um e-mail para o aluno e outro para o professor. A aula fica marcada com
`lembreteEnviado: true`, então cada aula recebe um único lembrete. O código está em
[servidor/src/main/java/easyagenda/lembretes/](servidor/src/main/java/easyagenda/lembretes/).

**Sem configurar nada, nenhum e-mail sai de verdade:** o texto de cada lembrete aparece no log
do servidor (`docker compose logs -f servidor`). Para enviar de verdade, configure um SMTP:

| Variável                      | Padrão                 | Para que serve                                  |
|-------------------------------|------------------------|-------------------------------------------------|
| `SMTP_HOST`                   | (vazio = só no log)    | Servidor SMTP, ex.: `smtp.gmail.com`            |
| `SMTP_PORT`                   | `587`                  | Porta do SMTP                                   |
| `SMTP_USUARIO`                | (vazio)                | Usuário do SMTP (normalmente o e-mail)          |
| `SMTP_SENHA`                  | (vazio)                | Senha do SMTP (no Gmail, uma "senha de app")    |
| `SMTP_REMETENTE`              | o `SMTP_USUARIO`       | Endereço que aparece como remetente             |
| `SMTP_TLS`                    | `true`                 | Usa STARTTLS                                    |
| `LEMBRETE_ANTECEDENCIA_HORAS` | `24`                   | Com quanto tempo de antecedência o lembrete sai |
| `LEMBRETE_INTERVALO_MINUTOS`  | `10`                   | De quanto em quanto tempo a rotina roda         |

> Nunca coloque a senha do SMTP no código nem no git: use o arquivo `.env` (que já está no
> `.gitignore`) ou variáveis de ambiente.

## Versão de teste no ar (tarefa 2.4)

| Parte    | Onde fica                                                  | Custo  |
|----------|------------------------------------------------------------|--------|
| Banco    | MongoDB Atlas (cluster M0)                                 | Grátis |
| Servidor | Render (Docker, configurado pelo [render.yaml](render.yaml)) | Grátis |
| Site     | Vercel (pasta [site/](site/))                              | Grátis |

### 1. Banco no MongoDB Atlas

1. Crie um cluster **M0** (grátis), na região `us-east-1` ou `sa-east-1`.
2. Em **Database Access**, crie um usuário com senha.
3. Em **Network Access**, libere `0.0.0.0/0` (o Render não tem IP fixo).
4. Crie as coleções e índices (pule se o Atlas do time já tiver):

   ```bash
   mongosh "<URI>" servidor/mongo-init/init.js
   ```

5. Em **Connect > Drivers**, copie a connection string (com usuário e senha). Ela é o
   `MONGODB_URI`.

### 2. Servidor no Render

1. No painel do Render: **New > Blueprint** e escolha este repositório. Ele lê o
   [render.yaml](render.yaml).
2. Preencha o `MONGODB_URI` com a connection string do Atlas (o SMTP é opcional; vazio, os
   lembretes saem só no log).
3. Depois do deploy, confira https://SEU-SERVICO.onrender.com/ping: a resposta deve ser `ok`.

Todo push na `main` faz um novo deploy.

> **O plano grátis adormece o servidor depois de 15 min sem acesso.** O primeiro acesso depois
> disso demora cerca de 1 min, e a rotina de lembretes fica parada enquanto ele dorme. O workflow
> [manter-servidor-acordado.yml](.github/workflows/manter-servidor-acordado.yml) chama o `/ping`
> a cada 10 min: para ligá-lo, crie a variável `SERVIDOR_URL` (o endereço do Render, sem `/` no
> fim) em **Settings > Secrets and variables > Actions > Variables** do GitHub.

### 3. Site na Vercel

1. No painel da Vercel: **Add New > Project** e escolha este repositório.
2. Em **Root Directory**, coloque `site`.
3. Em **Environment Variables**, crie `VITE_API_URL` com o endereço do Render, sem `/` no fim
   (ex.: `https://easyagenda-servidor.onrender.com`).
4. Clique em **Deploy**.

Se mudar o `VITE_API_URL` depois, faça um **Redeploy**: a variável entra no build do site.

### 4. Conferindo

No endereço da Vercel, cadastre um professor, crie um horário, abra o link do professor,
cadastre um aluno e agende a aula. Se der erro, veja os logs do serviço no Render e confira se o
`VITE_API_URL` está certo.

## Testes

Os testes de horários e de agendamento usam o MongoDB local (no banco separado `easyagenda_test`,
que é apagado a cada execução). Suba o banco antes:

```bash
docker compose up -d mongo
cd servidor
mvn test
```

O teste modelo é [PingTest.java](servidor/src/test/java/easyagenda/PingTest.java): copie-o para
criar novos testes. O nome da classe precisa terminar em `Test` (ex.: `AulaTest`), senão o Maven
não a executa.

## Problemas comuns

- **`permission denied while trying to connect to the docker API` (Linux):** seu usuário não está no
  grupo `docker`, ou ainda não fez logout/login depois de entrar nele. Rode
  `sudo usermod -aG docker $USER`, **saia da sessão e entre de novo** e confira com `id -nG`.
- **`failed to resolve reference "docker.io/..."` / `server misbehaving`:** falha de DNS ou internet
  ao baixar as imagens. Normalmente é momentânea: tente de novo. Se persistir, reinicie o roteador
  ou a conexão de rede.
- **Container `mongo` fica `unhealthy` com `MongoDB cannot start: Linux kernel versions 6.19 and
  newer...`:** é uma incompatibilidade do MongoDB 8 com kernels Linux novos. Por isso o projeto usa
  `mongo:7`; se alguém trocar a versão no `docker-compose.yml`, volte para `mongo:7`.

- **O servidor fecha logo ao iniciar (erro de conexão com o MongoDB):** o banco não está
  acessível. Confira se o container está rodando (`docker compose ps`) ou se a `MONGODB_URI` está
  correta.
- **Não conecta no Atlas:** no painel do Atlas, em *Network Access*, libere o seu IP. Confira
  também usuário e senha da URI.
- **`port is already allocated` / `Address already in use`:** a porta 8080 ou 27017 já está em
  uso. Pare o outro processo ou mude a porta (`PORT=8081` no `.env` ou no terminal).
- **Erro de versão do Java ao compilar (`release version 25 not supported`):** seu JDK é mais
  antigo. Instale o JDK 25 ou use a Opção 1.
- **Mudanças no `init.js` não apareceram:** o script só roda com o banco vazio. Rode
  `docker compose down -v` e suba de novo.

## Validação das semanas 2 e 3 (tarefa 3.4)

O [roteiro e relatório de testes](docs/testes-semanas-2-e-3.md) registra os
cenários executados, os resultados locais e a pendência de validação publicada.
O roteiro de navegador pode ser reproduzido com `npm run test:e2e` na pasta
`site/`, com o site e a API ativos conforme as instruções do relatório.
