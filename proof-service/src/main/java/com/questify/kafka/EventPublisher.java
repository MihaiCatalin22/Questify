package com.questify.kafka;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class EventPublisher {
    private final KafkaTemplate<String, Object> kafka;
    public EventPublisher(KafkaTemplate<String, Object> kafka) { this.kafka = kafka; }

    public <T> void publish(String topic, String key, String type, int version, String source, T payload) {
        var env = EventEnvelope.of(type, version, source, key, payload);
        kafka.send(topic, key, env);
    }
}
