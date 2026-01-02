package com.questify.gdpr;

import com.questify.domain.Quest;
import com.questify.kafka.EventEnvelope;
import com.questify.repository.QuestRepository;
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

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class GdprExportResponder {

    private final QuestRepository questRepo;

    @Value("${user.service.base:http://user-service}")
    private String userServiceBase;

    @Value("${SECURITY_INTERNAL_TOKEN:${INTERNAL_TOKEN:dev-internal-token}}")
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

            Map<String, Object> part = buildExportPart(userId, jobId);

            userServiceClient()
                    .post()
                    .uri("/internal/export-jobs/{jobId}/parts/{service}", jobId, "quest-service")
                    .header("X-Internal-Token", internalToken)
                    .bodyValue(part)
                    .retrieve()
                    .toBodilessEntity()
                    .block();

            log.info("Sent quest-service export part for jobId={}, userId={}", jobId, userId);
            ack.acknowledge();
        } catch (WebClientResponseException e) {
            log.error("quest-service failed posting export part: status={} body={}",
                    e.getRawStatusCode(), e.getResponseBodyAsString(), e);
            throw e;
        } catch (Exception e) {
            log.error("Failed processing UserExportRequested in quest-service: {}", e.toString(), e);
            throw e instanceof RuntimeException re ? re : new RuntimeException(e);
        }
    }

    private Map<String, Object> buildExportPart(String userId, String jobId) {
        List<Quest> created = questRepo
                .findByCreatedByUserId(userId, org.springframework.data.domain.Pageable.unpaged())
                .getContent();

        List<Map<String, Object>> createdDtos = created.stream().map(this::questDto).toList();

        LinkedHashMap<String, Object> out = new LinkedHashMap<>();
        out.put("service", "quest-service");
        out.put("jobId", jobId);
        out.put("userId", userId);
        out.put("generatedAt", Instant.now());
        out.put("questsCreated", createdDtos);
        return out;
    }

    private Map<String, Object> questDto(Quest q) {
        LinkedHashMap<String, Object> m = new LinkedHashMap<>();
        m.put("id", q.getId());
        m.put("title", q.getTitle());
        m.put("description", q.getDescription());
        m.put("status", q.getStatus());
        m.put("category", q.getCategory());
        m.put("visibility", q.getVisibility());
        m.put("startDate", q.getStartDate());
        m.put("endDate", q.getEndDate());
        m.put("createdAt", q.getCreatedAt());
        m.put("updatedAt", q.getUpdatedAt());
        return m;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object o) {
        if (o instanceof Map<?, ?> m) {
            LinkedHashMap<String, Object> out = new LinkedHashMap<>();
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
