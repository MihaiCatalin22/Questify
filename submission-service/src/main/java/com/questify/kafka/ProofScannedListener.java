package com.questify.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    private final ObjectMapper mapper;

    public ProofScannedListener(SubmissionService submissionService, ObjectMapper mapper) {
        this.submissionService = submissionService;
        this.mapper = mapper;
    }

    @KafkaListener(
            topics = "${app.kafka.topics.proofScanned:proof-scanned}",
            groupId = "${app.kafka.groups.proofScanned:submission-service-proof-scanned}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onProofScanned(Object payload, Acknowledgment ack) {
        try {
            String key = null;
            String scanStatus = null;

            if (payload instanceof String s) {
                JsonNode n = mapper.readTree(s);
                key = textOrNull(n, "key");
                scanStatus = textOrNull(n, "scanStatus");
            } else if (payload instanceof Map<?, ?> m) {
                Object k = m.get("key");
                Object st = m.get("scanStatus");
                key = k != null ? k.toString() : null;
                scanStatus = st != null ? st.toString() : null;
            } else {
                Map<String, Object> m = mapper.convertValue(payload, Map.class);
                Object k = m.get("key");
                Object st = m.get("scanStatus");
                key = k != null ? k.toString() : null;
                scanStatus = st != null ? st.toString() : null;
            }

            if (key == null || key.isBlank() || scanStatus == null || scanStatus.isBlank()) {
                log.warn("Ignoring proof-scanned message (missing key/scanStatus): {}", payload);
                ack.acknowledge();
                return;
            }

            submissionService.applyProofScanResult(key, scanStatus);
            ack.acknowledge();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static String textOrNull(JsonNode n, String field) {
        JsonNode v = n.get(field);
        if (v == null || v.isNull()) return null;
        String s = v.asText();
        return (s == null || s.isBlank()) ? null : s;
    }
}
