plugins {
    id("java")
    id("org.springframework.boot") version "3.5.5"
    id("io.spring.dependency-management") version "1.1.7"
    id("jacoco")
    id("org.sonarqube") version "6.3.1.5724"
}

group = "com.questify"
version = "0.0.1-SNAPSHOT"

java{
    toolchain {languageVersion.set(JavaLanguageVersion.of(21))}
}

configurations{
    compileOnly {
        extendsFrom(configurations.annotationProcessor.get())
    }
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
    implementation("org.springframework.kafka:spring-kafka")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.boot:spring-boot-starter-cache")
    implementation("software.amazon.awssdk:s3:2.25.39")
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.13")

    implementation("org.flywaydb:flyway-core:11.13.0")
    runtimeOnly("com.mysql:mysql-connector-j")

    implementation("io.jsonwebtoken:jjwt-api:0.11.5")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.11.5")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.11.5")
    runtimeOnly("com.h2database:h2")
    testRuntimeOnly("com.h2database:h2")
    implementation("io.micrometer:micrometer-registry-prometheus:1.15.1")
    implementation("io.micrometer:micrometer-tracing-bridge-otel:1.5.3")
    implementation("io.opentelemetry:opentelemetry-exporter-otlp:1.54.0")

    implementation("org.mapstruct:mapstruct:1.6.3")
    annotationProcessor("org.mapstruct:mapstruct-processor:1.6.3")
    compileOnly("org.projectlombok:lombok:1.18.42")
    annotationProcessor("org.projectlombok:lombok:1.18.42")
    annotationProcessor("org.projectlombok:lombok-mapstruct-binding:0.2.0")
    testImplementation("org.springframework.boot:spring-boot-starter-test") {
        exclude(group = "org.junit.vintage", module = "junit-vintage-engine")
    }
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.springframework.kafka:spring-kafka-test")

//    testImplementation(platform("org.testcontainers:testcontainers-bom:1.21.3"))
//    testImplementation("org.testcontainers:junit-jupiter")
//    testImplementation("org.testcontainers:mysql")
//    testImplementation("org.testcontainers:kafka")
//    testImplementation("org.testcontainers:redis")

    testRuntimeOnly("com.h2database:h2")

    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")


}

tasks.test {
    useJUnitPlatform()
    finalizedBy(tasks.jacocoTestReport)
}

jacoco { toolVersion = "0.8.11" }

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports { xml.required.set(true); html.required.set(true) }
    classDirectories.setFrom(
        files(classDirectories.files.map {
            fileTree(it) {
               // exclude("**/config/**", "**/dto/**", "**/api/**", "**/mapper/**")
            }
        })
    )
}

sonar {
    properties {
        property("sonar.projectKey", "MihaiCatalin22_Questify")
        property("sonar.organization", "mihaicatalin22")
//        property("sonar.organization", System.getenv("SONAR_ORG") ?: "local")
        property("sonar.host.url", "https://sonarcloud.io")
        property("sonar.coverage.jacoco.xmlReportPaths", "build/reports/jacoco/test/jacocoTestReport.xml")
        property("sonar.qualitygate.wait", "true")
    }
}