package com.questify.gdpr;

import com.questify.domain.Quest;
import com.questify.domain.QuestCompletion;
import com.questify.domain.QuestParticipant;
import com.questify.kafka.EventEnvelope;
import com.questify.repository.QuestCompletionRepository;
import com.questify.repository.QuestParticipantRepository;
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

import java.time.Instant;
import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class GdprExportResponder {

    private final QuestRepository questRepo;
    private final QuestParticipantRepository participantRepo;
    private final QuestCompletionRepository completionRepo;

    @Value("${user.service.base:http://user-service}")
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
        } catch (Exception e) {
            log.error("Failed processing UserExportRequested in quest-service: {}", e.toString(), e);
            throw e instanceof RuntimeException re ? re : new RuntimeException(e);
        }
    }

    private Map<String, Object> buildExportPart(String userId, String jobId) {
        List<Quest> created = questRepo.findByCreatedByUserId(userId, org.springframework.data.domain.Pageable.unpaged()).getContent();

        List<Map<String, Object>> createdDtos = created.stream().map(this::questDto).toList();

        return new LinkedHashMap<>(Map.of(
                "service", "quest-service",
                "jobId", jobId,
                "userId", userId,
                "generatedAt", Instant.now(),
                "questsCreated", createdDtos,
                "note", "Participation/completion export can be added by extending repositories with findByUserId."
        ));
    }

    private Map<String, Object> questDto(Quest q) {
        return new LinkedHashMap<>(Map.of(
                "id", q.getId(),
                "title", q.getTitle(),
                "description", q.getDescription(),
                "status", q.getStatus(),
                "category", q.getCategory(),
                "visibility", q.getVisibility(),
                "startDate", q.getStartDate(),
                "endDate", q.getEndDate(),
                "createdAt", q.getCreatedAt(),
                "updatedAt", q.getUpdatedAt()
        ));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object o) {
        if (o instanceof Map<?, ?> m) {
            Map<String, Object> out = new LinkedHashMap<>();
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
