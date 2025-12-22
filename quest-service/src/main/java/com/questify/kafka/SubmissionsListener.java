package com.questify.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SubmissionsListener {

    private final SubmissionEventsHandler handler;

    @KafkaListener(
            topics = "${app.kafka.topics.submissions}",
            groupId = "${spring.application.name}"
    )
    public void onSubmissionEvent(ConsumerRecord<String, EventEnvelope> rec, Acknowledgment ack) {
        try {
            var env = rec.value();
            if (env == null) {
                log.warn("Null envelope on {} partition={} offset={}", rec.topic(), rec.partition(), rec.offset());
                ack.acknowledge();
                return;
            }

            handler.handle(env);
            ack.acknowledge();
        } catch (Exception e) {
            throw e;
        }
    }
}
