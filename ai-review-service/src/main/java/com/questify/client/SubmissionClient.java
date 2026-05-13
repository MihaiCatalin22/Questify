package com.questify.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Slf4j
@Component
public class SubmissionClient {
    private final WebClient http;
    private final String internalToken;

    private static String firstNonBlank(String... values) {
        if (values == null) return "";
        for (String value : values) {
            if (value != null && !value.isBlank()) return value.trim();
        }
        return "";
    }

    public SubmissionClient(@Value("${submission.service.base:http://submission-service}") String baseUrl,
                            @Value("${internal.token:}") String internalDotToken,
                            @Value("${SECURITY_INTERNAL_TOKEN:}") String securityInternalToken,
                            @Value("${INTERNAL_TOKEN:dev-internal-token}") String internalToken) {
        this.http = WebClient.builder().baseUrl(baseUrl).build();
        this.internalToken = firstNonBlank(internalDotToken, securityInternalToken, internalToken);
    }

    public SubmissionContext getSubmissionContext(Long submissionId) {
        SubmissionContextRes res;
        try {
            res = http.get()
                    .uri("/internal/submissions/{id}/ai-review-context", submissionId)
                    .header("X-Internal-Token", internalToken)
                    .header("X-Security-Internal-Token", internalToken)
                    .retrieve()
                    .bodyToMono(SubmissionContextRes.class)
                    .block(Duration.ofSeconds(5));
        } catch (WebClientResponseException.NotFound notFound) {
            log.warn("Submission context not found submissionId={} status=404", submissionId);
            return null;
        } catch (WebClientResponseException e) {
            log.error("Submission context fetch failed submissionId={} status={} body={}",
                    submissionId, e.getStatusCode().value(), truncate(e.getResponseBodyAsString(), 300));
            throw e;
        }

        if (res == null) return null;
        log.info("Submission context fetched submissionId={} questId={} proofKeys={}",
                submissionId, res.questId(), res.proofKeys() == null ? 0 : res.proofKeys().size());
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

    private static String truncate(String value, int max) {
        if (value == null || value.length() <= max) return value;
        return value.substring(0, max);
    }
}
