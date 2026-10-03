# Build context is the repository root (a multi-module Maven reactor needs every pom.xml
# to resolve), so this is built with: docker build -f customer-service/Dockerfile .

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace

COPY pom.xml .
COPY customer-service/pom.xml customer-service/pom.xml
COPY account-service/pom.xml account-service/pom.xml
COPY card-service/pom.xml card-service/pom.xml
COPY data-generator/pom.xml data-generator/pom.xml
RUN mvn -q -B -pl customer-service dependency:go-offline

COPY customer-service/src customer-service/src
RUN mvn -q -B -pl customer-service package -DskipTests

FROM eclipse-temurin:21-jre-alpine AS runtime
RUN apk add --no-cache curl \
    && addgroup -S novabank && adduser -S novabank -G novabank

# Application Insights Java agent (OpenTelemetry-based): attached here, configured purely
# via the APPLICATIONINSIGHTS_CONNECTION_STRING env var at runtime — no connection string
# or config file baked into the image.
ARG APPINSIGHTS_AGENT_VERSION=3.7.10
RUN curl -fsSL -o /app-insights-agent.jar \
    "https://repo1.maven.org/maven2/com/microsoft/azure/applicationinsights-agent/${APPINSIGHTS_AGENT_VERSION}/applicationinsights-agent-${APPINSIGHTS_AGENT_VERSION}.jar"

WORKDIR /app
COPY --from=build /workspace/customer-service/target/customer-service-*.jar app.jar
USER novabank

EXPOSE 8081
HEALTHCHECK --interval=10s --timeout=3s --start-period=25s --retries=5 \
    CMD curl -f http://localhost:8081/actuator/health || exit 1

ENTRYPOINT ["java", "-javaagent:/app-insights-agent.jar", "-jar", "/app/app.jar"]
