# BurntToast Arcade -- multi-stage build: Gradle assembles the fat jar, the
# runtime image is a slim JRE. Data (SQLite) lives on the /data volume.

FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY . .
# The wrapper pins the same Gradle version used locally and in CI.
RUN ./gradlew :server:fatJar --no-daemon

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /src/server/build/libs/server-all.jar app.jar

ENV GAMES_PORT=8080 \
    GAMES_DB=/data/arcade.db
VOLUME /data
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
