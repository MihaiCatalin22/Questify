package com.questify.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.questify.config.CoachProperties;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;

@Component("coachRuntime")
public class CoachRuntimeHealthIndicator implements HealthIndicator {

    private final WebClient http;
    private final CoachProperties properties;

    public CoachRuntimeHealthIndicator(WebClient.Builder builder, CoachProperties properties) {
        this.http = builder
                .baseUrl(properties.getRuntimeBaseUrl())
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
        this.properties = properties;
    }

    @Override
    public Health health() {
        if (!"ollama".equals(properties.normalizedRuntime())) {
            return Health.up()
                    .withDetail("runtime", properties.normalizedRuntime())
                    .withDetail("configuredModel", properties.getModel())
                    .withDetail("reachable", false)
                    .withDetail("note", "Runtime-specific health probe is only implemented for Ollama")
                    .build();
        }

        try {
            JsonNode response = http.get()
                    .uri("/api/tags")
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .timeout(Duration.ofSeconds(3))
                    .block();

            int modelCount = response != null && response.has("models") && response.get("models").isArray()
                    ? response.get("models").size()
                    : 0;

            return Health.up()
                    .withDetail("runtime", properties.normalizedRuntime())
                    .withDetail("configuredModel", properties.getModel())
                    .withDetail("reachable", true)
                    .withDetail("availableModelCount", modelCount)
                    .build();
        } catch (Exception ex) {
            return Health.up()
                    .withDetail("runtime", properties.normalizedRuntime())
                    .withDetail("configuredModel", properties.getModel())
                    .withDetail("reachable", false)
                    .withDetail("error", ex.getClass().getSimpleName())
                    .build();
        }
    }
}
