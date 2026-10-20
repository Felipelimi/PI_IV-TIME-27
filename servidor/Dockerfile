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
EXPOSE 8080
CMD ["java", "-cp", "app.jar:lib/*", "easyagenda.Main"]
