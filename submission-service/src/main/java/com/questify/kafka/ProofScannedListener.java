package com.questify.kafka;

import com.questify.service.SubmissionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
public class ProofScannedListener {

    private final SubmissionService submissionService;

    public ProofScannedListener(SubmissionService submissionService) {
        this.submissionService = submissionService;
    }

    @KafkaListener(
            topics = "${app.kafka.topics.proofScanned:proof-scanned}",
            groupId = "${app.kafka.groups.proofScanned:submission-service-proof-scanned}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onProofScanned(EventEnvelope<Map<String, Object>> env, Acknowledgment ack) {
        try {
            Map<String, Object> p = env.payload();

            String proofKey = str(p.get("proofKey"));
            if (isBlank(proofKey)) proofKey = str(p.get("key"));
            if (isBlank(proofKey)) proofKey = env.partitionKey();

            String scanStatus = str(p.get("scanStatus"));

            if (isBlank(proofKey) || isBlank(scanStatus)) {
                log.warn("Ignoring proof-scanned (missing proofKey/scanStatus). env={}", env);
                ack.acknowledge();
                return;
            }

            submissionService.applyProofScanResult(proofKey, scanStatus);
            ack.acknowledge();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static String str(Object o) { return o == null ? null : o.toString(); }
    private static boolean isBlank(String s) { return s == null || s.isBlank(); }
}
