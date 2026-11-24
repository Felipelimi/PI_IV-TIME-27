# Etapa 1: compila o projeto e copia as dependências para target/lib
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q dependency:go-offline
COPY src ./src
RUN mvn -q package -DskipTests \
    && mvn -q dependency:copy-dependencies -DincludeScope=runtime -DoutputDirectory=target/lib

# Etapa 2: imagem final só com o JRE
FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /app/target/easyagenda-server-*.jar app.jar
COPY --from=build /app/target/lib ./lib
COPY iniciar.sh .
# Dois processos Java dividem a memória: no plano grátis do Render são 512 MB no total
ENV MEMORIA_API="-Xmx224m" \
    MEMORIA_SOCKETS="-Xmx96m" \
    SOCKETS_PORTA=3000
# 8080: API HTTP (pública). 3000: servidor de sockets, só dentro do container.
EXPOSE 8080
CMD ["bash", "iniciar.sh"]
