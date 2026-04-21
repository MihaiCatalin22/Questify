package com.questify.service;

import com.questify.dto.CoachDtos.CoachSuggestionRes;
import com.questify.dto.CoachDtos.CoachSuggestionsRes;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;

@Component
public class FallbackFactory {

    private final Clock clock;

    public FallbackFactory(Clock clock) {
        this.clock = clock;
    }

    public CoachSuggestionsRes create(CoachPromptContext context) {
        String goalFocus = goalFocus(context.goal());
        String primaryCategory = primaryCategory(context.goal());
        return new CoachSuggestionsRes(
                "FALLBACK",
                "SYSTEM",
                null,
                Instant.now(clock).truncatedTo(ChronoUnit.SECONDS),
                List.of(
                        new CoachSuggestionRes(
                                "Take one step toward " + goalFocus,
                                "Spend 15 minutes on one concrete action that moves " + goalFocus + " forward without trying to do everything at once.",
                                primaryCategory,
                                15,
                                "easy",
                                "A small action is more reliable than waiting for a perfect plan."
                        ),
                        new CoachSuggestionRes(
                                "Prepare the next session for " + goalFocus,
                                "Remove one point of friction for " + goalFocus + " by setting up materials, time, or your environment in advance.",
                                "HABIT",
                                10,
                                "easy",
                                "Preparation makes the next quest easier to start."
                        ),
                        new CoachSuggestionRes(
                                "Review what helped " + goalFocus,
                                "Write a short note on what is working, what is blocking you, and the next realistic move for " + goalFocus + ".",
                                primaryCategory,
                                10,
                                "easy",
                                "A quick review turns effort into a clearer next step."
                        )
                ),
                reflection(context),
                nudge(context)
        );
    }

    private static String goalFocus(String goal) {
        if (goal == null || goal.isBlank() || "No explicit goal provided.".equals(goal)) {
            return "your current goal";
        }

        String trimmed = goal.trim().replaceAll("\\s+", " ");
        if (trimmed.length() <= 36) {
            return trimmed;
        }
        return trimmed.substring(0, 33).trim() + "...";
    }

    private static String primaryCategory(String goal) {
        String normalized = goal == null ? "" : goal.toLowerCase(Locale.ROOT);
        if (containsAny(normalized, "health", "healthy", "fitness", "exercise", "workout", "walk", "run", "gym")) {
            return "FITNESS";
        }
        if (containsAny(normalized, "study", "learn", "learning", "course", "exam", "read", "reading")) {
            return "STUDY";
        }
        if (containsAny(normalized, "work", "career", "project", "job", "productivity", "code", "coding")) {
            return "WORK";
        }
        if (containsAny(normalized, "draw", "music", "guitar", "art", "write", "writing", "creative", "hobby")) {
            return "HOBBY";
        }
        if (containsAny(normalized, "friend", "family", "community", "social", "volunteer")) {
            return "COMMUNITY";
        }
        return "HABIT";
    }

    private static boolean containsAny(String haystack, String... needles) {
        for (String needle : needles) {
            if (haystack.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private static String reflection(CoachPromptContext context) {
        if (context.activeQuestCount() > 0) {
            return "You already have active quests in flight, so the safest backup is a small next step that adds momentum without creating overload.";
        }
        return "The coach fell back to a system-generated pass, so these suggestions stay intentionally small, clear, and easy to start.";
    }

    private static String nudge(CoachPromptContext context) {
        if (!context.activeQuestTitles().isEmpty()) {
            return "Pick the easiest card or pair one with an active quest you already have open.";
        }
        return "Pick the easiest card and do five minutes now so the next step is real, not theoretical.";
    }
}
