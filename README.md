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

## Testes

```bash
cd servidor
mvn test
```

Os testes não precisam de banco. O teste modelo é
[PingTest.java](servidor/src/test/java/easyagenda/PingTest.java): copie-o para criar novos testes.

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
