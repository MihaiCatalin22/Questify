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
    private final String primaryModel;
    private final String fallbackModel;
    private final int timeoutMs;
    private final String keepAlive;
    private final int maxOutputTokens;

    public OllamaVisionClient(@Value("${ai-review.runtime-base-url:http://ollama:11434}") String baseUrl,
                              @Value("${ai-review.model-primary:${AI_REVIEW_MODEL:qwen2.5vl:3b}}") String primaryModel,
                              @Value("${ai-review.model-fallback:}") String fallbackModel,
                              @Value("${ai-review.timeout-ms:120000}") int timeoutMs,
                              @Value("${ai-review.keep-alive:10m}") String keepAlive,
                              @Value("${ai-review.max-output-tokens:220}") int maxOutputTokens) {
        this.http = WebClient.builder().baseUrl(baseUrl).build();
        this.primaryModel = primaryModel;
        this.fallbackModel = fallbackModel;
        this.timeoutMs = timeoutMs;
        this.keepAlive = keepAlive;
        this.maxOutputTokens = maxOutputTokens;
    }

    @Override
    @SuppressWarnings("unchecked")
    public ModelResponse generate(AiReviewPrompt prompt) {
        Exception primaryError = null;
        try {
            String content = generateWithModel(primaryModel, prompt);
            return new ModelResponse(content, primaryModel, false, null);
        } catch (Exception ex) {
            primaryError = ex;
            log.warn("AI review primary model failed model={} error={}", primaryModel, ex.toString());
        }

        if (fallbackModel == null || fallbackModel.isBlank() || fallbackModel.equals(primaryModel)) {
            if (primaryError instanceof RuntimeException runtimeException) throw runtimeException;
            throw new IllegalStateException("Primary model failed and no distinct fallback model configured", primaryError);
        }

        try {
            String content = generateWithModel(fallbackModel, prompt);
            return new ModelResponse(content, fallbackModel, true, primaryError == null ? null : primaryError.toString());
        } catch (Exception fallbackError) {
            throw new IllegalStateException(
                    "Both primary and fallback models failed. primary=" + primaryModel + " fallback=" + fallbackModel,
                    fallbackError
            );
        }
    }

    @SuppressWarnings("unchecked")
    private String generateWithModel(String model, AiReviewPrompt prompt) {
        log.info("AI review model request model={} images={} promptChars={} timeoutMs={}",
                model,
                prompt.base64Images() == null ? 0 : prompt.base64Images().size(),
                prompt.textPrompt() == null ? 0 : prompt.textPrompt().length(),
                timeoutMs);

        Map<String, Object> body = Map.of(
                "model", model,
                "stream", false,
                "format", "json",
                "keep_alive", keepAlive,
                "messages", List.of(
                        Map.of("role", "system", "content", "You are an advisory proof reviewer. Return only compact JSON."),
                        Map.of("role", "user", "content", prompt.textPrompt(), "images", prompt.base64Images())
                ),
                "options", Map.of(
                        "temperature", 0.1,
                        "num_predict", maxOutputTokens
                )
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
        if (response != null && response.get("error") != null) {
            throw new IllegalStateException("Model error response: " + response.get("error"));
        }
        return "";
    }
}
