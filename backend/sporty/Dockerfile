FROM eclipse-temurin:17-jre

# 이미지 생성 전에 Gradle bootJar 또는 build 실행 필요

RUN groupadd --gid 10001 appgroup \
    && useradd --uid 10001 --gid appgroup --no-create-home appuser

WORKDIR /app

COPY --chown=10001:10001 build/libs/*SNAPSHOT.jar app.jar

USER 10001:10001

EXPOSE 8000

ENTRYPOINT ["java", "-jar", "app.jar"]