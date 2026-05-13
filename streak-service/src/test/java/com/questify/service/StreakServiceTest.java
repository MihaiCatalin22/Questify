package com.questify.service;

import com.questify.domain.CreditedCompletion;
import com.questify.domain.StreakActivityDay;
import com.questify.domain.StreakProfile;
import com.questify.repository.CreditedCompletionRepository;
import com.questify.repository.StreakActivityDayRepository;
import com.questify.repository.StreakProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StreakServiceTest {
    @Mock CreditedCompletionRepository credits;
    @Mock StreakActivityDayRepository days;
    @Mock StreakProfileRepository profiles;

    StreakService service;

    @BeforeEach
    void setUp() {
        service = new StreakService(
                credits,
                days,
                profiles,
                Clock.fixed(Instant.parse("2026-05-06T10:00:00Z"), ZoneId.of("UTC"))
        );
    }

    @Test
    void applyQuestCompleted_awards_100_xp_per_new_completion_and_one_active_day() {
        when(credits.existsBySubmissionId(99L)).thenReturn(false);
        when(profiles.findById("u1")).thenReturn(Optional.empty());
        when(days.findByUserIdAndActivityDate("u1", LocalDate.parse("2026-05-05"))).thenReturn(Optional.empty());
        when(days.existsByUserIdAndActivityDate("u1", LocalDate.parse("2026-05-04"))).thenReturn(true);
        when(credits.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(days.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(profiles.save(any())).thenAnswer(inv -> inv.getArgument(0));

        StreakProfile profile = service.applyQuestCompleted(new StreakService.QuestCompleted(
                5L, "u1", 99L,
                Instant.parse("2026-05-05T20:30:00Z"),
                Instant.parse("2026-05-06T08:00:00Z")
        ));

        assertThat(profile.getTotalXp()).isEqualTo(100);
        assertThat(profile.getTotalCompletions()).isEqualTo(1);
        assertThat(profile.getTotalActiveDays()).isEqualTo(1);
        assertThat(profile.getLongestStreak()).isEqualTo(2);
        assertThat(profile.getLastActiveDate()).isEqualTo(LocalDate.parse("2026-05-05"));
        verify(credits).save(any(CreditedCompletion.class));
        verify(days).save(any(StreakActivityDay.class));
    }

    @Test
    void applyQuestCompleted_ignores_duplicate_submission_credit() {
        when(credits.existsBySubmissionId(99L)).thenReturn(true);

        StreakProfile out = service.applyQuestCompleted(new StreakService.QuestCompleted(
                5L, "u1", 99L,
                Instant.parse("2026-05-05T20:30:00Z"),
                Instant.parse("2026-05-06T08:00:00Z")
        ));

        assertThat(out).isNull();
        verifyNoInteractions(days, profiles);
    }

    @Test
    void summary_currentStreak_is_zero_after_missed_day() {
        StreakProfile profile = StreakProfile.builder()
                .userId("u1")
                .totalXp(300)
                .totalCompletions(3)
                .totalActiveDays(3)
                .longestStreak(3)
                .lastActiveDate(LocalDate.parse("2026-05-03"))
                .build();
        when(profiles.findById("u1")).thenReturn(Optional.of(profile));

        StreakService.StreakSummary summary = service.summary("u1");

        assertThat(summary.currentStreak()).isZero();
        assertThat(summary.level()).isEqualTo(2);
    }
}
