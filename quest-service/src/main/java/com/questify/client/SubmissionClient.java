package com.questify.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
public class SubmissionClient {

    private final WebClient http;
    private final String internalToken;

    public SubmissionClient(
            @Value("${SUBMISSION_SERVICE_BASE:http://submission-service:8080}") String base,
            @Value("${INTERNAL_TOKEN:dev-internal-token}") String internalToken
    ) {
        this.http = WebClient.builder()
                .baseUrl(base)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
        this.internalToken = internalToken;
    }

    public boolean isCompleted(Long questId, String userId) {
        try {
            var res = http.get()
                    .uri(uri -> uri.path("/internal/completions/check")
                            .queryParam("questId", questId)
                            .queryParam("userId", userId)
                            .build())
                    .header("X-Internal-Token", internalToken)
                    .retrieve()
                    .bodyToMono(CheckRes.class)
                    .block();
            return res != null && Boolean.TRUE.equals(res.completed);
        } catch (Exception e) {
            return false;
        }
    }

    private record CheckRes(Long questId, String userId, Boolean completed) {}
}
