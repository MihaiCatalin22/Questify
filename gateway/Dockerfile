# syntax=docker/dockerfile:1
FROM gradle:8.14-jdk21 AS build
WORKDIR /workspace

COPY build.gradle.kts settings.gradle.kts gradle.properties* ./
COPY gradle gradle
RUN gradle --no-daemon -g /home/gradle/.gradle help

COPY . .
RUN gradle --no-daemon -g /home/gradle/.gradle clean bootJar -x test

FROM eclipse-temurin:24-jre
WORKDIR /app
ENV JAVA_OPTS=""
COPY --from=build /workspace/build/libs/*.jar /app/app.jar
EXPOSE 8080
ENTRYPOINT ["sh","-c","java $JAVA_OPTS -jar /app/app.jar"]
