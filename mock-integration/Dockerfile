# syntax=docker/dockerfile:1
# Multi-stage build: compile with Maven, run on a slim JRE. The same shape as
# ../backend/Dockerfile — the only difference is the port.
#
# The --mount=type=cache for ~/.m2 is shared with the backend's build, so the second Java
# image in this repo does not re-download Spring.

FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
COPY src/ src/
RUN --mount=type=cache,target=/root/.m2,sharing=locked \
    ./mvnw -B -q clean package -DskipTests

FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app
# curl is used by the compose healthcheck to hit /health.
RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
COPY --from=build /app/target/*.jar app.jar
# 8081 everywhere — on a laptop, and as the third container of the same ECS task, where the
# backend reaches it on 127.0.0.1:8081 because awsvpc gives one network namespace per task.
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
