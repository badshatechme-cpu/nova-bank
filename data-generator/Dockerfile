# Build context is the repository root (a multi-module Maven reactor needs every pom.xml
# to resolve), so this is built with: docker build -f data-generator/Dockerfile .

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace

COPY pom.xml .
COPY customer-service/pom.xml customer-service/pom.xml
COPY account-service/pom.xml account-service/pom.xml
COPY card-service/pom.xml card-service/pom.xml
COPY data-generator/pom.xml data-generator/pom.xml
RUN mvn -q -B -pl data-generator dependency:go-offline

COPY data-generator/src data-generator/src
RUN mvn -q -B -pl data-generator package -DskipTests

FROM eclipse-temurin:21-jre-alpine AS runtime
RUN addgroup -S novabank && adduser -S novabank -G novabank
WORKDIR /app
COPY --from=build /workspace/data-generator/target/data-generator-*.jar app.jar
USER novabank

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
