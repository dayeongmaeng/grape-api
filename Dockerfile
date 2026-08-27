# syntax=docker/dockerfile:1

# ---- build stage: compile + package the bootJar with the project's Gradle wrapper ----
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

COPY --chmod=0755 gradlew ./
COPY gradle ./gradle
COPY settings.gradle build.gradle ./
COPY src ./src

# BuildKit cache mount keeps the Gradle distribution + dependency cache across builds.
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew --no-daemon --console=plain clean bootJar

# ---- runtime stage: JRE + jar only ----
FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app

RUN groupadd --system spring && useradd --system --gid spring --home-dir /app spring
COPY --from=build /workspace/build/libs/*.jar /app/app.jar
USER spring:spring

# App always listens on 8080 inside the container (Lightsail maps the public port to this).
EXPOSE 8080

# Flyway applies migrations on startup. Config comes from the environment (see AGENTS.md).
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
