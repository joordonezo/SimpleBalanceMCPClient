# Etapa 1: build
FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /mcp
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

# Etapa 2: runtime
FROM eclipse-temurin:17-jre
WORKDIR /mcp-client
COPY --from=build /mcp/target/*.jar mcp-client.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "mcp-client.jar"]
