package com.questify.kafka;

import com.questify.service.AiReviewService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class SubmissionCreatedListener {
    private final AiReviewService reviews;

    @KafkaListener(
            topics = "${app.kafka.topics.submissions:dev.questify.submissions}",
            groupId = "${app.kafka.groups.aiReview:ai-review-service}"
    )
    public void onSubmissionEvent(EventEnvelope env, Acknowledgment ack) {
        try {
            if (env == null || !"SubmissionCreated".equals(env.eventType())) return;
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = (Map<String, Object>) env.payload();
            Long submissionId = toLong(payload.get("submissionId"));
            Long questId = toLong(payload.get("questId"));
            String userId = str(payload.get("userId"));
            if (submissionId == null || questId == null || userId == null) {
                log.warn("Ignoring SubmissionCreated missing required fields: {}", payload);
                return;
            }
            reviews.reviewSubmission(new AiReviewService.SubmissionCreated(
                    submissionId,
                    questId,
                    userId,
                    str(payload.get("note")),
                    toInstant(payload.get("submittedAt")),
                    proofKeys(payload)
            ));
        } finally {
            if (ack != null) ack.acknowledge();
        }
    }

    private static List<String> proofKeys(Map<String, Object> payload) {
        List<String> keys = new ArrayList<>();
        Object list = payload.get("proofKeys");
        if (list instanceof Iterable<?> iterable) {
            for (Object key : iterable) {
                if (key != null && !String.valueOf(key).isBlank()) keys.add(String.valueOf(key));
            }
        }
        Object single = payload.get("proofKey");
        if (single != null && !String.valueOf(single).isBlank() && !keys.contains(String.valueOf(single))) {
            keys.add(String.valueOf(single));
        }
        return keys;
    }

    private static Long toLong(Object value) {
        if (value == null) return null;
        if (value instanceof Number number) return number.longValue();
        try { return Long.parseLong(String.valueOf(value)); } catch (Exception e) { return null; }
    }

    private static Instant toInstant(Object value) {
        if (value == null) return null;
        if (value instanceof Instant instant) return instant;
        try { return Instant.parse(String.valueOf(value)); } catch (Exception e) { return null; }
    }

    private static String str(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
