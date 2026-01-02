package com.questify.gdpr;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.questify.kafka.EventEnvelope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class GdprExportResponder {

    private final ProofMetadataExporter exporter;
    private final ObjectMapper mapper;

    @Value("${user.service.base:${USER_SERVICE_BASE:http://user-service}}")
    private String userServiceBase;

    @Value("${SECURITY_INTERNAL_TOKEN:${INTERNAL_TOKEN:dev-internal-token}}")
    private String internalToken;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    @PostConstruct
    void logConfig() {
        boolean tokenPresent = internalToken != null && !internalToken.isBlank();
        log.info("proof-service GDPR responder configured: userServiceBase={}, tokenPresent={}", userServiceBase, tokenPresent);

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

            var proofObjects = exporter.listProofObjectsForUser(userId);

            Map<String, Object> part = new LinkedHashMap<>();
            part.put("service", "proof-service");
            part.put("jobId", jobId);
            part.put("userId", userId);
            part.put("generatedAt", Instant.now());
            part.put("proofObjects", proofObjects);

            postPart(jobId, "proof-service", part);

            log.info("Sent proof-service export part for jobId={}, userId={}", jobId, userId);
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Failed processing UserExportRequested in proof-service: {}", e.toString(), e);
            throw e instanceof RuntimeException re ? re : new RuntimeException(e);
        }
    }

    private void postPart(String jobId, String service, Map<String, Object> part) throws Exception {
        String url = userServiceBase + "/internal/export-jobs/" + jobId + "/parts/" + service;
        String json = mapper.writeValueAsString(part);

        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .header("X-Internal-Token", internalToken)
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() >= 300) {
            log.error("user-service callback failed: status={} body={}", res.statusCode(), res.body());
            throw new IllegalStateException("user-service callback failed: " + res.statusCode());
        }
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
