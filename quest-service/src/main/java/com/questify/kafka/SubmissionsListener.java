package com.questify.kafka;

import com.questify.service.CompletionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class SubmissionsListener {

    private final CompletionService completionService;

    @Value("${app.kafka.topics.submissions}")
    private String submissionsTopic;

    @KafkaListener(topics = "${app.kafka.topics.submissions}",
            groupId = "${spring.application.name}")
    public void onSubmissionEvent(ConsumerRecord<String, EventEnvelope> rec, Acknowledgment ack) {
        try {
            var env = rec.value();
            if (env == null) {
                log.warn("Null envelope on {} partition={} offset={}", rec.topic(), rec.partition(), rec.offset());
                ack.acknowledge();
                return;
            }

            if ("SubmissionReviewed".equals(env.eventType())) {
                @SuppressWarnings("unchecked")
                var p = (Map<String, Object>) env.payload();
                var reviewStatus = String.valueOf(p.get("reviewStatus"));
                if ("APPROVED".equalsIgnoreCase(reviewStatus)) {
                    Long questId      = toLong(p.get("questId"));
                    Long submissionId = toLong(p.get("submissionId"));
                    String userId     = str(p.get("userId"));

                    if (userId == null && submissionId != null) {
                        log.info("SubmissionReviewed APPROVED (questId={}, submissionId={}); userId unknown", questId, submissionId);
                    }

                    if (questId != null && userId != null) {
                        completionService.upsertCompleted(questId, userId, submissionId);
                        log.info("Marked quest {} completed for user {} via event {}", questId, userId, env.eventId());
                    } else {
                        throw new IllegalStateException("Missing questId and/or userId in SubmissionReviewed payload: " + p);
                    }
                }
            }

            ack.acknowledge();
        } catch (Exception e) {
            throw e;
        }
    }

    private static Long toLong(Object o) {
        if (o == null) return null;
        if (o instanceof Number n) return n.longValue();
        try { return Long.parseLong(String.valueOf(o)); } catch (Exception e) { return null; }
    }
    private static String str(Object o) { return o == null ? null : String.valueOf(o); }
}
