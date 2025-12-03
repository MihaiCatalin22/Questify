package com.questify.kafka;

import java.time.Instant;
import java.util.UUID;

public record EventEnvelope<T>(
        String eventId,
        String eventType,
        int eventVersion,
        Instant occurredAt,
        String source,
        String traceId,
        String partitionKey,
        T payload
) {
    public static <T> EventEnvelope<T> of(String type, int ver, String src, String key, T payload) {
        return new EventEnvelope<>(
                UUID.randomUUID().toString(),
                type, ver,
                Instant.now(),
                src,
                UUID.randomUUID().toString(),
                key,
                payload
        );
    }
}
