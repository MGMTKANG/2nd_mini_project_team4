FROM gradle:8.14-jdk17 AS builder
USER gradle
WORKDIR /home/gradle
COPY --chown=gradle:gradle build.gradle settings.gradle ./
COPY --chown=gradle:gradle src/main ./src/main
RUN gradle --no-daemon bootJar

FROM eclipse-temurin:17.0.17_10-jre-noble
RUN groupadd --system --gid 10001 sporty \
    && useradd --system --uid 10001 --gid sporty --create-home sporty
WORKDIR /app
COPY --from=builder --chown=sporty:sporty /home/gradle/build/libs/api-gateway-*.jar ./app.jar
USER sporty
EXPOSE 8000
ENTRYPOINT ["java", "-jar", "/app/app.jar"]