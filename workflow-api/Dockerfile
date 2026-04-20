# ── Stage 1: Build ───────────────────────────────────────────────────────────
FROM maven:3.9.6-eclipse-temurin-21 AS builder
WORKDIR /build

# Copy parent POM first (layer cache optimization)
COPY pom.xml .
COPY common/pom.xml common/
COPY workflow-api/pom.xml workflow-api/
COPY task-runner/pom.xml task-runner/
COPY recovery-service/pom.xml recovery-service/
COPY compensation-service/pom.xml compensation-service/

# Download dependencies (cached unless pom.xml changes)
RUN mvn dependency:go-offline -pl common,workflow-api -am -q

# Copy source
COPY common/src common/src
COPY workflow-api/src workflow-api/src

# Build only workflow-api and its dependency (common)
RUN mvn package -pl common,workflow-api -am -DskipTests -q

# ── Stage 2: Runtime ─────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /build/workflow-api/target/workflow-api-1.0.0.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
