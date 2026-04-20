# ── Stage 1: Build ───────────────────────────────────────────────────────────
FROM maven:3.9.6-eclipse-temurin-21 AS builder
WORKDIR /build

COPY pom.xml .
COPY common/pom.xml common/
COPY workflow-api/pom.xml workflow-api/
COPY task-runner/pom.xml task-runner/
COPY recovery-service/pom.xml recovery-service/
COPY compensation-service/pom.xml compensation-service/

RUN mvn dependency:go-offline -pl common,task-runner -am -q

COPY common/src common/src
COPY task-runner/src task-runner/src

RUN mvn package -pl common,task-runner -am -DskipTests -q

# ── Stage 2: Runtime ─────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /build/task-runner/target/task-runner-1.0.0.jar app.jar
EXPOSE 8083
ENTRYPOINT ["java", "-jar", "app.jar"]
