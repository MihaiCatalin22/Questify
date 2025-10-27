package com.questify.domain;


import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity @Getter
@Setter @AllArgsConstructor @NoArgsConstructor
@Builder @ToString(exclude = {"quest", "user", "submission"})
@Table(name = "quest_completions",
        uniqueConstraints = @UniqueConstraint(name = "uq_quest_user", columnNames = {"quest_id","user_id"}))
public class QuestCompletion {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "quest_id", nullable = false)
    private Quest quest;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "submission_id")
    private Submission submission;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private CompletionStatus status = CompletionStatus.COMPLETED;

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

    public enum CompletionStatus { COMPLETED, REVOKED }
}

