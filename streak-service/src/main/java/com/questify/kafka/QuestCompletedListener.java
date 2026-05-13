package com.questify.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.questify.service.StreakService;
import java.time.Instant;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class QuestCompletedListener {
    private final ObjectMapper mapper;
    private final StreakService streakService;

    @KafkaListener(topics = "${app.kafka.topics.streaks:dev.questify.streaks}", groupId = "${spring.kafka.consumer.group-id:streak-service}")
    public void onEvent(ConsumerRecord<String, EventEnvelope> record, Acknowledgment ack) {
        EventEnvelope envelope = record.value();
        if (envelope != null && "QuestCompleted".equals(envelope.eventType())) {
            Map<?, ?> payload = mapper.convertValue(envelope.payload(), Map.class);
            streakService.applyQuestCompleted(new StreakService.QuestCompleted(
                    longValue(payload.get("questId")),
                    stringValue(payload.get("userId")),
                    longValue(payload.get("submissionId")),
                    instantValue(payload.get("submittedAt")),
                    instantValue(payload.get("completedAt"))
            ));
        }
        ack.acknowledge();
    }

    private Long longValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.valueOf(value.toString());
    }

    private String stringValue(Object value) {
        return value == null ? null : value.toString();
    }

    private Instant instantValue(Object value) {
        return value == null || value.toString().isBlank() ? null : Instant.parse(value.toString());
    }
}
