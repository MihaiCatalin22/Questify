package com.questify.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Component
public class SubmissionClient {
    private final WebClient http;
    private final String internalToken;

    public SubmissionClient(@Value("${submission.service.base:http://submission-service}") String baseUrl,
                            @Value("${internal.token:${INTERNAL_TOKEN:dev-internal-token}}") String internalToken) {
        this.http = WebClient.builder().baseUrl(baseUrl).build();
        this.internalToken = internalToken;
    }

    public SubmissionContext getSubmissionContext(Long submissionId) {
        SubmissionContextRes res;
        try {
            res = http.get()
                    .uri("/internal/submissions/{id}/ai-review-context", submissionId)
                    .header("X-Internal-Token", internalToken)
                    .retrieve()
                    .bodyToMono(SubmissionContextRes.class)
                    .block(Duration.ofSeconds(5));
        } catch (WebClientResponseException.NotFound notFound) {
            return null;
        }

        if (res == null) return null;
        return new SubmissionContext(
                res.submissionId(),
                res.questId(),
                res.userId(),
                res.note(),
                res.submittedAt(),
                res.proofKeys() == null ? List.of() : res.proofKeys()
        );
    }

    public record SubmissionContext(
            Long submissionId,
            Long questId,
            String userId,
            String note,
            Instant submittedAt,
            List<String> proofKeys
    ) {}

    private record SubmissionContextRes(
            Long submissionId,
            Long questId,
            String userId,
            String note,
            Instant submittedAt,
            List<String> proofKeys
    ) {}
}
