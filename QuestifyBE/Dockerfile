FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

COPY gradlew ./gradlew
COPY gradle ./gradle
COPY build.gradle* settings.gradle* gradle.properties* ./

RUN sed -i 's/\r$//' ./gradlew && chmod +x ./gradlew && ./gradlew --no-daemon --version

# Copy sources
COPY src ./src

ARG SKIP_TESTS=true
RUN if [ "$SKIP_TESTS" = "true" ]; then \
      ./gradlew --no-daemon clean bootJar -x test ; \
    else \
      ./gradlew --no-daemon clean test jacocoTestReport bootJar ; \
    fi

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S app && adduser -S app -G app
USER app

COPY --from=build /app/build/libs/*.jar /app/app.jar

EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]
