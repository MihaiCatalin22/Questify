package com.questify.kafka;

import java.time.Instant;

public record EventEnvelope(
        String eventId,
        String eventType,
        int version,
        String source,
        String aggregateId,
        Instant occurredAt,
        Object payload
) {}
