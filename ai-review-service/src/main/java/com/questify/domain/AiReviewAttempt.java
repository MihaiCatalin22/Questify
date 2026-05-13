package com.questify.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(
        name = "ai_review_attempts",
        indexes = {
                @Index(name = "idx_ai_attempt_submission", columnList = "submission_id"),
                @Index(name = "idx_ai_attempt_reviewed_at", columnList = "reviewed_at")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiReviewAttempt {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "submission_id", nullable = false)
    private Long submissionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "run_source", nullable = false, length = 32)
    private AiReviewRunSource runSource;

    @Column(name = "triggered_by", length = 191)
    private String triggeredBy;

    @Column(name = "outcome", nullable = false, length = 48)
    private String outcome;

    @Enumerated(EnumType.STRING)
    @Column(name = "recommendation", length = 32)
    private AiReviewRecommendation recommendation;

    @Column(name = "confidence")
    private Double confidence;

    @Lob
    @Column(name = "detail")
    private String detail;

    @Column(name = "reviewed_at", nullable = false)
    private Instant reviewedAt;

    @PrePersist
    void prePersist() {
        if (reviewedAt == null) reviewedAt = Instant.now();
    }
}
