package com.questify.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

@Slf4j
@Component
public class AiReviewClient {
    private final WebClient http;
    private final String internalToken;

    public AiReviewClient(
            @Value("${AI_REVIEW_SERVICE_BASE:http://ai-review-service}") String aiReviewBase,
            @Value("${INTERNAL_TOKEN:dev-internal-token}") String internalToken
    ) {
        this.http = WebClient.builder()
                .baseUrl(aiReviewBase)
                .clientConnector(new ReactorClientHttpConnector(HttpClient.create()))
                .build();
        this.internalToken = internalToken;
    }

    public void triggerReview(Long submissionId) {
        try {
            http.post()
                    .uri("/internal/ai-reviews/submissions/{id}/run", submissionId)
                    .header("X-Internal-Token", internalToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .toBodilessEntity()
                    .block(Duration.ofSeconds(5));
        } catch (Exception e) {
            log.warn("AI review fallback trigger failed for submissionId={} err={}", submissionId, e.toString());
        }
    }
}
