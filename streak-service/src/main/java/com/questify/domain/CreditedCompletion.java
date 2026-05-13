package com.questify.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "credited_completions")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreditedCompletion {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String userId;

    @Column(nullable = false)
    private Long questId;

    @Column(unique = true)
    private Long submissionId;

    @Column(nullable = false)
    private LocalDate activityDate;

    private Instant submittedAt;

    @Column(nullable = false)
    private Instant completedAt;

    @Column(nullable = false)
    private Integer xpAwarded;

    @Column(nullable = false)
    private Instant creditedAt;
}
