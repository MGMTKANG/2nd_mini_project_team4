FROM eclipse-temurin:17-jre

WORKDIR /app

COPY build/libs/*SNAPSHOT.jar app.jar

EXPOSE 8000

ENTRYPOINT ["java", "-jar", "app.jar"]