# syntax=docker/dockerfile:1
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
ARG SERVICE
COPY . .
RUN --mount=type=cache,target=/root/.m2,sharing=locked \
    sh ./mvnw -B --no-transfer-progress -pl "$SERVICE" -am package -DskipTests && \
    cp "$SERVICE/target/$SERVICE-0.0.1-SNAPSHOT.jar" /workspace/service.jar

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN groupadd --system app && useradd --system --gid app app
COPY --from=build --chown=app:app /workspace/service.jar /app/service.jar
USER app
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70"
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/service.jar"]
