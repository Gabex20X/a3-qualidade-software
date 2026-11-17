# Estagio 1: dependencias + codigo. Tambem e a imagem do servico "testes".
FROM maven:3.9-eclipse-temurin-21 AS testes
WORKDIR /app
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
CMD ["mvn", "-B", "verify"]

# Estagio 2: empacota o jar (sem rodar testes; eles rodam no servico "testes").
FROM testes AS build
RUN mvn -B -q package -DskipTests

# Estagio 3: imagem final, so com o JRE e o jar.
FROM eclipse-temurin:21-jre AS app
WORKDIR /app
COPY --from=build /app/target/financas-api.jar app.jar
ENV PORT=8080 \
    DB_URL=jdbc:sqlite:/app/dados/financas.db
EXPOSE 8080
CMD ["java", "-jar", "app.jar"]
