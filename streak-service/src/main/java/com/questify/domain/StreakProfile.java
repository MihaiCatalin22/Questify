package com.questify.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "streak_profiles")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StreakProfile {
    @Id
    private String userId;

    @Column(nullable = false)
    private Integer totalXp;

    @Column(nullable = false)
    private Integer totalCompletions;

    @Column(nullable = false)
    private Integer totalActiveDays;

    @Column(nullable = false)
    private Integer longestStreak;

    private LocalDate lastActiveDate;

    @Column(nullable = false)
    private Instant updatedAt;
}
