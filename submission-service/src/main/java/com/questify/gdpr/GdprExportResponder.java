package com.questify.gdpr;

import com.questify.domain.Submission;
import com.questify.kafka.EventEnvelope;
import com.questify.repository.SubmissionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class GdprExportResponder {

    private final SubmissionRepository repo;

    @Value("${USER_SERVICE_BASE:http://user-service}")
    private String userServiceBase;

    @Value("${internal.token:${INTERNAL_TOKEN:dev-internal-token}}")
    private String internalToken;

    private WebClient userServiceClient() {
        return WebClient.builder()
                .baseUrl(userServiceBase)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @KafkaListener(
            topics = "${app.kafka.topics.users}",
            groupId = "${spring.application.name}-gdpr-export"
    )
    public void on(EventEnvelope<?> env, Acknowledgment ack) {
        try {
            if (env == null || !"UserExportRequested".equals(env.eventType())) {
                ack.acknowledge();
                return;
            }

            Map<String, Object> payload = asMap(env.payload());
            String jobId = str(payload.get("jobId"));
            String userId = str(payload.get("userId"));
            if (jobId == null || userId == null) {
                log.warn("UserExportRequested missing jobId/userId payload={}", payload);
                ack.acknowledge();
                return;
            }

            var submissions = repo.findByUserIdOrderByCreatedAtDesc(
                    userId, org.springframework.data.domain.Pageable.unpaged()
            ).getContent();

            Map<String, Object> part = Map.of(
                    "service", "submission-service",
                    "jobId", jobId,
                    "userId", userId,
                    "generatedAt", Instant.now(),
                    "submissions", submissions.stream().map(this::submissionDto).toList()
            );

            userServiceClient()
                    .post()
                    .uri("/internal/export-jobs/{jobId}/parts/{service}", jobId, "submission-service")
                    .header("X-Internal-Token", internalToken)
                    .bodyValue(part)
                    .retrieve()
                    .toBodilessEntity()
                    .block();

            log.info("Sent submission-service export part for jobId={}, userId={}", jobId, userId);
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Failed processing UserExportRequested in submission-service: {}", e.toString(), e);
            throw e instanceof RuntimeException re ? re : new RuntimeException(e);
        }
    }

    private Map<String, Object> submissionDto(Submission s) {
        var m = new LinkedHashMap<String, Object>();
        m.put("id", s.getId());
        m.put("questId", s.getQuestId());
        m.put("userId", s.getUserId());
        m.put("proofKey", s.getProofKey());
        m.put("note", s.getNote());
        m.put("status", s.getStatus());
        m.put("reviewerUserId", s.getReviewerUserId());
        m.put("reviewedAt", s.getReviewedAt());
        m.put("createdAt", s.getCreatedAt());
        m.put("updatedAt", s.getUpdatedAt());
        m.put("proofScanStatus", s.getProofScanStatus());
        m.put("proofScannedAt", s.getProofScannedAt());
        return m;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object o) {
        if (o instanceof Map<?, ?> m) {
            var out = new LinkedHashMap<String, Object>();
            m.forEach((k, v) -> out.put(String.valueOf(k), v));
            return out;
        }
        return Map.of();
    }

    private static String str(Object o) {
        if (o == null) return null;
        String s = String.valueOf(o);
        return s.isBlank() ? null : s;
    }
}
