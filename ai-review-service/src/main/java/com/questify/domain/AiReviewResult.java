package com.questify.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(
        name = "ai_review_results",
        uniqueConstraints = @UniqueConstraint(name = "uq_ai_review_submission", columnNames = "submission_id")
)
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
public class AiReviewResult {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "submission_id", nullable = false)
    private Long submissionId;

    @Column(name = "quest_id", nullable = false)
    private Long questId;

    @Column(name = "user_id", nullable = false, length = 191)
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private AiReviewRecommendation recommendation;

    @Enumerated(EnumType.STRING)
    @Column(length = 24)
    private AiReviewRunStatus status;

    @Column(nullable = false)
    private double confidence;

    @Column
    private double supportScore;

    @Column(nullable = false, length = 128)
    private String model;

    @Lob
    @Column(nullable = false)
    private String reasons;

    @Column(length = 500)
    private String decisionNote;

    @Column(nullable = false)
    private boolean generatedPolicy;

    @Column(length = 128)
    private String modelUsed;

    @Column(nullable = false)
    private boolean fallbackUsed;

    @Column(length = 500)
    private String fallbackReason;

    @Lob
    private String matchedEvidence;

    @Lob
    private String missingEvidence;

    @Lob
    private String matchedDisqualifiers;

    @Lob
    private String ocrSnippets;

    @Lob
    private String observedSignals;

    @Column(length = 500)
    private String decisionPath;

    @Lob
    private String rawOutput;

    @Column(nullable = false)
    private boolean mediaSupported;

    @Column(nullable = false)
    private Instant reviewedAt;

    @PrePersist
    void prePersist() {
        if (status == null) status = AiReviewRunStatus.PENDING;
        if (reviewedAt == null) reviewedAt = Instant.now();
    }
}
