# EasyAgenda – Servidor

Servidor do EasyAgenda em **Java 25 LTS**, usando:

- `HttpServer` do próprio JDK para as rotas HTTP
- Driver oficial do **MongoDB** (`mongodb-driver-sync`)
- **JUnit 5** para os testes
- **Maven** para build e dependências

## Pré-requisitos

- JDK 25 ou mais novo
- Maven 3.8+
- Um MongoDB rodando (local ou Atlas)

## Configuração

Variáveis de ambiente (todas opcionais):

| Variável      | Padrão                       |
|---------------|------------------------------|
| `PORT`        | `8080`                       |
| `MONGODB_URI` | `mongodb://localhost:27017`  |
| `MONGODB_DB`  | `easyagenda`                 |

Exemplo com Atlas (Linux/macOS):

```bash
export MONGODB_URI="mongodb+srv://USUARIO:SENHA@cluster.xxxxx.mongodb.net"
```

No Windows (PowerShell): `$env:MONGODB_URI="mongodb+srv://..."`

## Como rodar

```bash
mvn compile exec:java
```

Teste no navegador: http://localhost:8080/ping → `ok`

## Testes

```bash
mvn test
```

O teste modelo está em `src/test/java/easyagenda/PingTest.java`.

## Estrutura

```
src/main/java/easyagenda/
  Main.java       # ponto de entrada: conecta no banco e sobe o servidor
  Database.java   # conexão com o MongoDB
  Server.java     # servidor HTTP e rotas
src/test/java/easyagenda/
  PingTest.java   # teste modelo
```
