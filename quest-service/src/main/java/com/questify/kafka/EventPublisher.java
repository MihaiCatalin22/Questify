package com.questify.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.questify.consistency.OutboxEvent;
import com.questify.consistency.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class EventPublisher {

    private final OutboxEventRepository outbox;
    private final ObjectMapper mapper;
    private final KafkaTemplate<String, Object> kafka;

    @Value("${app.outbox.enabled:true}")
    private boolean outboxEnabled;

    public <T> void publish(String topic, String key, String type, int version, String source, T payload) {
        var env = EventEnvelope.of(type, version, source, key, payload);

        if (!outboxEnabled) {
            kafka.send(topic, key, env);
            return;
        }

        try {
            String json = mapper.writeValueAsString(env);
            outbox.save(OutboxEvent.newEvent(env.eventId(), topic, key, json));
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize event envelope for outbox", e);
        }
    }
}
