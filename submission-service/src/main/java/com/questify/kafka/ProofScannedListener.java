package com.questify.kafka;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.questify.service.SubmissionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
public class ProofScannedListener {

    private final SubmissionService submissionService;
    private final ObjectMapper mapper;

    @Value("${app.kafka.groups.proofScanned:submission-service-proof-scanned}")
    private String proofScannedGroup;

    public ProofScannedListener(SubmissionService submissionService, ObjectMapper mapper) {
        this.submissionService = submissionService;
        this.mapper = mapper;
    }

    @KafkaListener(
            topics = "${app.kafka.topics.proofScanned:proof-scanned}",
            groupId = "${app.kafka.groups.proofScanned:submission-service-proof-scanned}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onProofScanned(Object msg, Acknowledgment ack) {
        try {
            String eventId = null;
            String proofKey = null;
            String scanStatus = null;

            if (msg instanceof EventEnvelope<?> env) {
                eventId = env.eventId();
                Object payloadObj = env.payload();
                if (payloadObj instanceof Map<?, ?> p) {
                    proofKey = str(firstNonBlank(p.get("proofKey"), p.get("key")));
                    scanStatus = str(p.get("scanStatus"));
                }
            }

            if (msg instanceof Map<?, ?> m) {
                eventId = str(m.get("eventId"));
                Object payloadObj = m.get("payload");
                if (payloadObj instanceof Map<?, ?> p) {
                    proofKey = str(firstNonBlank(p.get("proofKey"), p.get("key")));
                    scanStatus = str(p.get("scanStatus"));
                } else {
                    proofKey = str(firstNonBlank(m.get("proofKey"), m.get("key")));
                    scanStatus = str(m.get("scanStatus"));
                }
            }
            else if (msg instanceof String s) {
                Map<String, Object> m = mapper.readValue(s, new TypeReference<>() {});
                eventId = str(m.get("eventId"));
                Object payloadObj = m.get("payload");
                if (payloadObj instanceof Map<?, ?> p) {
                    proofKey = str(firstNonBlank(p.get("proofKey"), p.get("key")));
                    scanStatus = str(p.get("scanStatus"));
                } else {
                    proofKey = str(firstNonBlank(m.get("proofKey"), m.get("key")));
                    scanStatus = str(m.get("scanStatus"));
                }
            }
            else {
                Map<String, Object> m = mapper.convertValue(msg, new TypeReference<>() {});
                eventId = str(m.get("eventId"));
                Object payloadObj = m.get("payload");
                if (payloadObj instanceof Map<?, ?> p) {
                    proofKey = str(firstNonBlank(p.get("proofKey"), p.get("key")));
                    scanStatus = str(p.get("scanStatus"));
                } else {
                    proofKey = str(firstNonBlank(m.get("proofKey"), m.get("key")));
                    scanStatus = str(m.get("scanStatus"));
                }
            }

            if (isBlank(proofKey) || isBlank(scanStatus)) {
                log.warn("Ignoring proof-scanned message (missing proofKey/scanStatus): {}", msg);
                ack.acknowledge();
                return;
            }

            submissionService.applyProofScanResultIdempotent(proofScannedGroup, eventId, proofKey, scanStatus);
            ack.acknowledge();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static Object firstNonBlank(Object a, Object b) {
        String sa = str(a);
        if (!isBlank(sa)) return sa;
        String sb = str(b);
        if (!isBlank(sb)) return sb;
        return null;
    }

    private static String str(Object o) {
        return o == null ? null : o.toString();
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
