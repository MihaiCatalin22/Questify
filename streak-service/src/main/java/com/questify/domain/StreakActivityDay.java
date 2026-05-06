package com.questify.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
        name = "streak_activity_days",
        uniqueConstraints = @UniqueConstraint(name = "uk_streak_day_user_date", columnNames = {"userId", "activityDate"}),
        indexes = @Index(name = "idx_streak_day_user_date", columnList = "userId,activityDate")
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StreakActivityDay {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String userId;

    @Column(nullable = false)
    private LocalDate activityDate;

    @Column(nullable = false)
    private Integer completionsCount;

    @Column(nullable = false)
    private Integer xpEarned;

    @Column(nullable = false)
    private Instant firstCreditedAt;

    @Column(nullable = false)
    private Instant lastCreditedAt;
}
