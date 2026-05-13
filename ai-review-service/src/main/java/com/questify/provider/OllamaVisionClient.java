package com.questify.provider;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class OllamaVisionClient implements ModelClient {
    private final WebClient http;
    private final String model;
    private final int timeoutMs;

    public OllamaVisionClient(@Value("${ai-review.runtime-base-url:http://ollama:11434}") String baseUrl,
                              @Value("${ai-review.model:qwen2.5vl:3b}") String model,
                              @Value("${ai-review.timeout-ms:90000}") int timeoutMs) {
        this.http = WebClient.builder().baseUrl(baseUrl).build();
        this.model = model;
        this.timeoutMs = timeoutMs;
    }

    @Override
    @SuppressWarnings("unchecked")
    public String generate(AiReviewPrompt prompt) {
        log.info("AI review model request model={} images={} promptChars={} timeoutMs={}",
                model,
                prompt.base64Images() == null ? 0 : prompt.base64Images().size(),
                prompt.textPrompt() == null ? 0 : prompt.textPrompt().length(),
                timeoutMs);

        Map<String, Object> body = Map.of(
                "model", model,
                "stream", false,
                "format", "json",
                "messages", List.of(
                        Map.of("role", "system", "content", "You are an advisory proof reviewer. Return only compact JSON."),
                        Map.of("role", "user", "content", prompt.textPrompt(), "images", prompt.base64Images())
                ),
                "options", Map.of("temperature", 0.1)
        );
        Map<String, Object> response = http.post()
                .uri("/api/chat")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(Map.class)
                .block(Duration.ofMillis(timeoutMs));
        log.info("AI review model response model={} keys={}",
                model, response == null ? 0 : response.keySet().size());
        Object message = response == null ? null : response.get("message");
        if (message instanceof Map<?, ?> map && map.get("content") != null) {
            return String.valueOf(map.get("content"));
        }
        return "";
    }
}
