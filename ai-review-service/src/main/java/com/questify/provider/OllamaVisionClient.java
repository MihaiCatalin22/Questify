package com.questify.provider;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class OllamaVisionClient implements ModelClient {
    private final WebClient http;
    private final String primaryModel;
    private final String claimCheckModel;
    private final String fallbackModel;
    private final int timeoutMs;
    private final String keepAlive;
    private final int maxOutputTokens;

    public OllamaVisionClient(@Value("${ai-review.runtime-base-url:http://ollama:11434}") String baseUrl,
                              @Value("${ai-review.model-primary:${AI_REVIEW_MODEL:qwen2.5vl:3b}}") String primaryModel,
                              @Value("${ai-review.model-claim-check:${AI_REVIEW_MODEL_CLAIM_CHECK:}}") String claimCheckModel,
                              @Value("${ai-review.model-fallback:}") String fallbackModel,
                              @Value("${ai-review.timeout-ms:120000}") int timeoutMs,
                              @Value("${ai-review.keep-alive:10m}") String keepAlive,
                              @Value("${ai-review.max-output-tokens:420}") int maxOutputTokens) {
        this.http = WebClient.builder().baseUrl(baseUrl).build();
        this.primaryModel = primaryModel;
        this.claimCheckModel = claimCheckModel;
        this.fallbackModel = fallbackModel;
        this.timeoutMs = timeoutMs;
        this.keepAlive = keepAlive;
        this.maxOutputTokens = maxOutputTokens;
    }

    @Override
    @SuppressWarnings("unchecked")
    public ModelResponse generate(AiReviewPrompt prompt) {
        List<String> modelCandidates = modelCandidatesForPrompt(prompt);
        Exception firstError = null;
        Exception lastError = null;
        for (int idx = 0; idx < modelCandidates.size(); idx++) {
            String candidate = modelCandidates.get(idx);
            try {
                String content = generateWithModel(candidate, prompt);
                return new ModelResponse(content, candidate, idx > 0, firstError == null ? null : firstError.toString());
            } catch (Exception ex) {
                if (firstError == null) firstError = ex;
                lastError = ex;
                log.warn("AI review model candidate failed stage={} model={} error={}",
                        prompt.stage(), candidate, ex.toString());
            }
        }

        if (lastError == null) {
            throw new IllegalStateException("No AI review model candidates available for stage " + prompt.stage());
        }
        throw new IllegalStateException(
                "All AI review models failed for stage " + prompt.stage() + ". candidates=" + modelCandidates,
                lastError
        );
    }

    private List<String> modelCandidatesForPrompt(AiReviewPrompt prompt) {
        String stagePrimary = switch (prompt.stage()) {
            case CLAIM_CHECK -> firstNonBlank(claimCheckModel, primaryModel);
            default -> primaryModel;
        };
        LinkedHashSet<String> ordered = new LinkedHashSet<>();
        if (stagePrimary != null && !stagePrimary.isBlank()) ordered.add(stagePrimary.trim());
        if (primaryModel != null && !primaryModel.isBlank()) ordered.add(primaryModel.trim());
        if (fallbackModel != null && !fallbackModel.isBlank()) ordered.add(fallbackModel.trim());
        return new ArrayList<>(ordered);
    }

    @SuppressWarnings("unchecked")
    private String generateWithModel(String model, AiReviewPrompt prompt) {
        log.info("AI review model request stage={} model={} images={} promptChars={} timeoutMs={}",
                prompt.stage(),
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
        log.info("AI review model response stage={} model={} keys={}",
                prompt.stage(), model, response == null ? 0 : response.keySet().size());
        Object message = response == null ? null : response.get("message");
        if (message instanceof Map<?, ?> map && map.get("content") != null) {
            return String.valueOf(map.get("content"));
        }
        if (response != null && response.get("error") != null) {
            throw new IllegalStateException("Model error response: " + response.get("error"));
        }
        return "";
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) return first;
        return second;
    }
}
