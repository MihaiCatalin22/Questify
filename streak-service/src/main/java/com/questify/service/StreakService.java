package com.questify.service;

import com.questify.domain.CreditedCompletion;
import com.questify.domain.StreakActivityDay;
import com.questify.domain.StreakProfile;
import com.questify.repository.CreditedCompletionRepository;
import com.questify.repository.StreakActivityDayRepository;
import com.questify.repository.StreakProfileRepository;
import jakarta.transaction.Transactional;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.stereotype.Service;

@Service
public class StreakService {
    private static final int XP_PER_COMPLETION = 100;
    private static final ZoneId ACTIVITY_ZONE = ZoneId.of("Europe/Amsterdam");

    private final CreditedCompletionRepository credits;
    private final StreakActivityDayRepository days;
    private final StreakProfileRepository profiles;
    private final Clock clock;

    public StreakService(
            CreditedCompletionRepository credits,
            StreakActivityDayRepository days,
            StreakProfileRepository profiles,
            Clock clock) {
        this.credits = credits;
        this.days = days;
        this.profiles = profiles;
        this.clock = clock;
    }

    @Transactional
    public StreakProfile applyQuestCompleted(QuestCompleted event) {
        if (event.submissionId() != null && credits.existsBySubmissionId(event.submissionId())) {
            return null;
        }

        Instant now = Instant.now(clock);
        LocalDate activityDate = activityDate(event);
        StreakProfile profile = profiles.findById(event.userId()).orElseGet(() -> newProfile(event.userId(), now));
        StreakActivityDay day = days.findByUserIdAndActivityDate(event.userId(), activityDate)
                .orElseGet(() -> newDay(event.userId(), activityDate, now));
        boolean newActiveDay = day.getId() == null && day.getCompletionsCount() == 0;

        day.setCompletionsCount(day.getCompletionsCount() + 1);
        day.setXpEarned(day.getXpEarned() + XP_PER_COMPLETION);
        day.setLastCreditedAt(now);

        profile.setTotalXp(profile.getTotalXp() + XP_PER_COMPLETION);
        profile.setTotalCompletions(profile.getTotalCompletions() + 1);
        if (newActiveDay) {
            profile.setTotalActiveDays(profile.getTotalActiveDays() + 1);
        }
        profile.setLongestStreak(Math.max(profile.getLongestStreak(), streakEndingAt(event.userId(), activityDate)));
        if (profile.getLastActiveDate() == null || activityDate.isAfter(profile.getLastActiveDate())) {
            profile.setLastActiveDate(activityDate);
        }
        profile.setUpdatedAt(now);

        credits.save(CreditedCompletion.builder()
                .userId(event.userId())
                .questId(event.questId())
                .submissionId(event.submissionId())
                .activityDate(activityDate)
                .submittedAt(event.submittedAt())
                .completedAt(event.completedAt() == null ? now : event.completedAt())
                .xpAwarded(XP_PER_COMPLETION)
                .creditedAt(now)
                .build());
        days.save(day);
        return profiles.save(profile);
    }

    @Transactional
    public StreakSummary summary(String userId) {
        StreakProfile profile = profiles.findById(userId).orElseGet(() -> newProfile(userId, Instant.now(clock)));
        int currentStreak = currentStreak(profile);
        int level = levelForXp(profile.getTotalXp());
        int currentLevelStartXp = xpForLevel(level);
        int nextLevelXp = xpForLevel(level + 1);
        return new StreakSummary(
                profile.getUserId(),
                profile.getTotalXp(),
                level,
                Math.max(0, profile.getTotalXp() - currentLevelStartXp),
                Math.max(1, nextLevelXp - currentLevelStartXp),
                profile.getTotalCompletions(),
                profile.getTotalActiveDays(),
                currentStreak,
                profile.getLongestStreak(),
                profile.getLastActiveDate()
        );
    }

    private LocalDate activityDate(QuestCompleted event) {
        Instant basis = event.submittedAt() != null ? event.submittedAt() : event.completedAt();
        if (basis == null) {
            basis = Instant.now(clock);
        }
        return basis.atZone(ACTIVITY_ZONE).toLocalDate();
    }

    private int currentStreak(StreakProfile profile) {
        if (profile.getLastActiveDate() == null) {
            return 0;
        }
        LocalDate today = LocalDate.now(clock.withZone(ACTIVITY_ZONE));
        if (profile.getLastActiveDate().isBefore(today.minusDays(1))) {
            return 0;
        }
        return streakEndingAt(profile.getUserId(), profile.getLastActiveDate());
    }

    private int streakEndingAt(String userId, LocalDate endDate) {
        int count = 1;
        LocalDate cursor = endDate.minusDays(1);
        while (days.existsByUserIdAndActivityDate(userId, cursor)) {
            count++;
            cursor = cursor.minusDays(1);
        }
        return count;
    }

    private StreakProfile newProfile(String userId, Instant now) {
        return StreakProfile.builder()
                .userId(userId)
                .totalXp(0)
                .totalCompletions(0)
                .totalActiveDays(0)
                .longestStreak(0)
                .updatedAt(now)
                .build();
    }

    private StreakActivityDay newDay(String userId, LocalDate activityDate, Instant now) {
        return StreakActivityDay.builder()
                .userId(userId)
                .activityDate(activityDate)
                .completionsCount(0)
                .xpEarned(0)
                .firstCreditedAt(now)
                .lastCreditedAt(now)
                .build();
    }

    private int levelForXp(int xp) {
        return (int) Math.floor(Math.sqrt(xp / 250.0)) + 1;
    }

    private int xpForLevel(int level) {
        int completedLevels = Math.max(0, level - 1);
        return completedLevels * completedLevels * 250;
    }

    public record QuestCompleted(
            Long questId,
            String userId,
            Long submissionId,
            Instant submittedAt,
            Instant completedAt
    ) {}

    public record StreakSummary(
            String userId,
            int totalXp,
            int level,
            int levelXp,
            int nextLevelXp,
            int totalCompletions,
            int totalActiveDays,
            int currentStreak,
            int longestStreak,
            LocalDate lastActiveDate
    ) {}
}
