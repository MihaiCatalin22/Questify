package com.questify.provider;

import com.questify.config.CoachProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeoutException;

@Component
public class OllamaAdapter implements ModelClient {

    private final WebClient http;

    public OllamaAdapter(WebClient.Builder builder, CoachProperties properties) {
        this.http = builder
                .baseUrl(properties.getRuntimeBaseUrl())
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @Override
    public String generate(GenerationPrompt prompt, GenerationOptions options) {
        try {
            var response = http.post()
                    .uri("/api/generate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestBody(prompt, options))
                    .retrieve()
                    .bodyToMono(OllamaGenerateResponse.class)
                    .timeout(options.timeout())
                    .block();

            if (response == null || response.response() == null || response.response().isBlank()) {
                throw new ModelClientException("Ollama returned an empty response payload");
            }
            return response.response();
        } catch (Exception ex) {
            if (isTimeout(ex)) {
                throw new ModelTimeoutException("Timed out while calling Ollama", ex);
            }
            if (ex instanceof ModelClientException modelClientException) {
                throw modelClientException;
            }
            if (ex instanceof WebClientResponseException responseException) {
                throw new ModelClientException("Ollama returned HTTP " + responseException.getStatusCode().value(), ex);
            }
            throw new ModelClientException("Failed to call Ollama runtime", ex);
        }
    }

    private static Map<String, Object> requestBody(GenerationPrompt prompt, GenerationOptions options) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", options.model());
        body.put("prompt", prompt.userPrompt());
        body.put("system", prompt.systemPrompt());
        body.put("stream", false);
        body.put("format", options.jsonSchema());
        body.put("options", Map.of(
                "temperature", options.temperature(),
                "num_predict", options.maxOutputTokens()
        ));
        return body;
    }

    private static boolean isTimeout(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof TimeoutException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private record OllamaGenerateResponse(String response) {}
}
