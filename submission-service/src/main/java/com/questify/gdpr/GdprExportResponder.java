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
import org.springframework.web.reactive.function.client.WebClientResponseException;

import jakarta.annotation.PostConstruct;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class GdprExportResponder {

    private final SubmissionRepository repo;

    @Value("${user.service.base:${USER_SERVICE_BASE:http://user-service}}")
    private String userServiceBase;

    @Value("${SECURITY_INTERNAL_TOKEN:${INTERNAL_TOKEN:dev-internal-token}}")
    private String internalToken;

    private WebClient userServiceClient;

    @PostConstruct
    void init() {
        this.userServiceClient = WebClient.builder()
                .baseUrl(userServiceBase)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();

        boolean tokenPresent = internalToken != null && !internalToken.isBlank();
        log.info("submission-service GDPR responder configured: userServiceBase={}, tokenPresent={}", userServiceBase, tokenPresent);

        if (userServiceBase.contains(":8080")) {
            log.warn("userServiceBase contains :8080. If your K8s Service exposes port 80->8080, use http://user-service (no :8080).");
        }
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

            List<Submission> submissions = repo.findByUserIdOrderByCreatedAtDesc(
                    userId, org.springframework.data.domain.Pageable.unpaged()
            ).getContent();

            Map<String, Object> part = new LinkedHashMap<>();
            part.put("service", "submission-service");
            part.put("jobId", jobId);
            part.put("userId", userId);
            part.put("generatedAt", Instant.now());
            part.put("submissions", submissions.stream().map(this::submissionDto).toList());

            userServiceClient
                    .post()
                    .uri("/internal/export-jobs/{jobId}/parts/{service}", jobId, "submission-service")
                    .header("X-Internal-Token", internalToken)
                    .bodyValue(part)
                    .retrieve()
                    .toBodilessEntity()
                    .block();

            log.info("Sent submission-service export part for jobId={}, userId={}", jobId, userId);
            ack.acknowledge();
        } catch (WebClientResponseException e) {
            log.error("submission-service callback failed: status={} body={}",
                    e.getRawStatusCode(), e.getResponseBodyAsString(), e);
            throw e;
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
