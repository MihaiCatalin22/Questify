package com.questify.kafka;

import com.questify.service.ProofStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserEventsListener {

    private final ProofStorageService storage;

    @KafkaListener(
            topics = "${app.kafka.topics.users}",
            groupId = "${spring.application.name}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onUserEvent(ConsumerRecord<String, EventEnvelope> rec, Acknowledgment ack) {
        try {
            var env = rec.value();
            if (env == null) {
                log.warn("Null envelope on {} partition={} offset={}", rec.topic(), rec.partition(), rec.offset());
                ack.acknowledge();
                return;
            }

            if (!"UserDeleted".equals(env.eventType())) {
                ack.acknowledge();
                return;
            }

            Object payloadObj = env.payload();
            if (!(payloadObj instanceof Map<?, ?> p)) {
                throw new IllegalArgumentException("UserDeleted payload must be a map, got: " +
                        (payloadObj == null ? "null" : payloadObj.getClass().getName()));
            }

            String userId = p.get("userId") == null ? null : String.valueOf(p.get("userId"));
            if (userId == null || userId.isBlank()) {
                throw new IllegalArgumentException("Missing userId in UserDeleted payload: " + p);
            }

            String prefix = "proofs/" + userId + "/";
            long deleted = storage.deleteByPrefix(prefix);

            log.info("GDPR cleanup (proof-service): userId={} prefix={} deletedObjects={} eventId={}",
                    userId, prefix, deleted, env.eventId());

            ack.acknowledge();
        } catch (Exception e) {
            throw e;
        }
    }
}
