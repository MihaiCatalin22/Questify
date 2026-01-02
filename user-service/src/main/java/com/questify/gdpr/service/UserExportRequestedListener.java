package com.questify.gdpr.service;

import com.questify.kafka.EventEnvelope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserExportRequestedListener {

    private final UserExportService exports;
    private final UserProfileExportAssembler profileExporter;

    @KafkaListener(
            topics = "${app.kafka.topics.users}",
            groupId = "${spring.application.name}-gdpr-export"
    )
    public void onEvent(EventEnvelope<?> env, Acknowledgment ack) {
        try {
            if (env == null || !"UserExportRequested".equals(env.eventType())) {
                ack.acknowledge();
                return;
            }

            if (!(env.payload() instanceof Map<?, ?> p)) {
                log.warn("UserExportRequested payload is not a map: {}", env.payload());
                ack.acknowledge();
                return;
            }

            String jobId = String.valueOf(p.get("jobId"));
            String userId = String.valueOf(p.get("userId"));

            Map<String, Object> payload = profileExporter.exportForUser(userId);
            exports.receivePart(jobId, "user-service", payload);

            log.info("Uploaded user-service export part for jobId={} userId={}", jobId, userId);
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Failed handling UserExportRequested in user-service; will retry", e);
            // no ack => retry
        }
    }
}
