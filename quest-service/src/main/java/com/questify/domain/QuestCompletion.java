package com.questify.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(
        name = "quest_completions",
        uniqueConstraints = @UniqueConstraint(name = "uq_quest_user", columnNames = {"quest_id", "user_id"})
)
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class QuestCompletion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "quest_id", nullable = false)
    private Long questId;

    @Column(name = "user_id", nullable = false, length = 191)
    private String userId;

    @Column(name = "submission_id")
    private Long submissionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private QuestStatus status;

    @Column(name = "completed_at", nullable = false)
    private Instant completedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    public void prePersist() {
        Instant now = Instant.now();
        if (completedAt == null) completedAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = Instant.now();
    }
}
