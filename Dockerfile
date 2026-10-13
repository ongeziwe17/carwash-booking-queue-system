# syntax=docker/dockerfile:1.7

FROM maven:3.9.11-eclipse-temurin-21-alpine@sha256:922927df2c662cdd47ddb116443d6bec4696cfae3de1a0ddac8fcc7b87ce61ae AS builder

WORKDIR /workspace

COPY pom.xml .
COPY .mvn .mvn
COPY mvnw .

RUN --mount=type=cache,target=/root/.m2 \
    chmod +x mvnw \
    && ./mvnw --batch-mode dependency:go-offline

COPY src src

RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw --batch-mode clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine-3.23@sha256:42b42237d59d901504348d97b8bc304e6b5f4e12f5cf8f45c4f76fef2427aa84 AS runtime

WORKDIR /app

RUN addgroup -S -g 10001 appgroup \
    && adduser -S -u 10001 -G appgroup appuser

COPY --from=builder --chown=appuser:appgroup \
    /workspace/target/carwash-api.jar /app/app.jar

ENV JAVA_TOOL_OPTIONS="-XX:InitialRAMPercentage=20.0 -XX:MaxRAMPercentage=55.0 -XX:+ExitOnOutOfMemoryError -Djava.io.tmpdir=/tmp -Duser.home=/tmp"

USER 10001:10001

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
