package com.questify.kafka;

import com.questify.consistency.ProcessedEventService;
import com.questify.service.CompletionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubmissionEventsHandler {

    private final CompletionService completionService;
    private final ProcessedEventService processedEvents;

    @Value("${spring.application.name}")
    private String consumerGroup;

    @Transactional
    public void handle(EventEnvelope env) {
        if (env == null) return;

        if (!processedEvents.markProcessedIfNew(consumerGroup, env.eventId())) {
            log.info("Duplicate submission event skipped eventId={} type={}", env.eventId(), env.eventType());
            return;
        }

        if ("SubmissionReviewed".equals(env.eventType())) {
            @SuppressWarnings("unchecked")
            Map<String, Object> p = (Map<String, Object>) env.payload();

            String status = str(p.get("reviewStatus"));
            if (!"APPROVED".equalsIgnoreCase(status)) return;

            Long questId = toLong(p.get("questId"));
            String userId = str(p.get("userId"));
            Long submissionId = toLong(p.get("submissionId"));

            if (questId == null || userId == null) {
                throw new IllegalStateException("Missing questId and/or userId in SubmissionReviewed payload: " + p);
            }

            completionService.upsertCompleted(questId, userId, submissionId);
            log.info("Marked quest {} completed for user {} via event {}", questId, userId, env.eventId());
        }
    }

    private static Long toLong(Object o) {
        if (o == null) return null;
        if (o instanceof Number n) return n.longValue();
        try { return Long.parseLong(String.valueOf(o)); } catch (Exception e) { return null; }
    }
    private static String str(Object o) { return o == null ? null : String.valueOf(o); }
}
