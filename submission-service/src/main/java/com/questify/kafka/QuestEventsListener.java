package com.questify.kafka;

import com.questify.domain.ReviewStatus;
import com.questify.repository.SubmissionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class QuestEventsListener {

    private final SubmissionRepository submissions;

    @Value("${app.kafka.topics.quests}")
    private String questsTopic;

    @KafkaListener(
            topics = "${app.kafka.topics.quests}",
            groupId = "${spring.application.name}"
    )
    @Transactional
    public void onQuestEvent(ConsumerRecord<String, EventEnvelope> rec, Acknowledgment ack) {
        var env = rec.value();
        try {
            if (env == null) {
                log.warn("Null envelope on {} partition={} offset={}", rec.topic(), rec.partition(), rec.offset());
                ack.acknowledge();
                return;
            }

            if ("QuestArchived".equals(env.eventType())) {
                @SuppressWarnings("unchecked")
                var p = (Map<String, Object>) env.payload();
                Long questId = toLong(p.get("questId"));
                if (questId != null) {
                    int affected = submissions.rejectAllPendingForQuest(
                            questId, ReviewStatus.PENDING, ReviewStatus.REJECTED,
                            "Quest archived – auto-rejected."
                    );
                    log.info("Auto-rejected {} pending submissions for archived quest {}", affected, questId);
                } else {
                    log.warn("QuestArchived without questId payload={}", p);
                }
            }

            ack.acknowledge();
        } catch (Exception e) {
            // Let DefaultErrorHandler send to <topic>.dlq
            throw e;
        }
    }

    private static Long toLong(Object o) {
        if (o == null) return null;
        if (o instanceof Number n) return n.longValue();
        try { return Long.parseLong(String.valueOf(o)); } catch (Exception e) { return null; }
    }
}
