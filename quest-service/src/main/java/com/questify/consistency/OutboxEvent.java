package com.questify.consistency;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(
        name = "outbox_event",
        indexes = {
                @Index(name = "idx_outbox_status_next_created", columnList = "status,nextAttemptAt,createdAt")
        }
)
public class OutboxEvent {

    @Id
    @Column(length = 36, nullable = false)
    private String id;

    @Column(nullable = false, length = 200)
    private String topic;

    @Column(length = 512)
    private String key;

    @Lob
    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String envelopeJson;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private OutboxStatus status;

    @Column(nullable = false)
    private int attempts;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant nextAttemptAt;

    private Instant sentAt;

    @Column(length = 2000)
    private String lastError;

    public static OutboxEvent newEvent(String eventId, String topic, String key, String envelopeJson) {
        Instant now = Instant.now();
        return OutboxEvent.builder()
                .id(eventId)
                .topic(topic)
                .key(key)
                .envelopeJson(envelopeJson)
                .status(OutboxStatus.NEW)
                .attempts(0)
                .createdAt(now)
                .nextAttemptAt(now)
                .build();
    }
}
