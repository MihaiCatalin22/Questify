package com.questify.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public class CoachDtos {

    public enum CoachSuggestionMode {
        DEFAULT
    }

    public record CoachSuggestionsReq(
            CoachSuggestionMode mode,
            Boolean includeRecentHistory,
            List<String> excludedSuggestionTitles
    ) {
        public CoachSuggestionMode resolvedMode() {
            return mode == null ? CoachSuggestionMode.DEFAULT : mode;
        }

        public boolean resolvedIncludeRecentHistory() {
            return includeRecentHistory == null || includeRecentHistory;
        }

        public List<String> resolvedExcludedSuggestionTitles() {
            if (excludedSuggestionTitles == null || excludedSuggestionTitles.isEmpty()) {
                return List.of();
            }
            return excludedSuggestionTitles.stream()
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(title -> !title.isBlank())
                    .distinct()
                    .limit(12)
                    .toList();
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record CoachSuggestionsRes(
            String status,
            String source,
            String model,
            Instant generatedAt,
            List<CoachSuggestionRes> suggestions,
            String reflection,
            String nudge
    ) {}

    public record CoachSuggestionRes(
            String title,
            String description,
            String category,
            int estimatedMinutes,
            String difficulty,
            String reason
    ) {}

    public record UserCoachSettingsRes(
            boolean aiCoachEnabled,
            String coachGoal
    ) {}

    public record RecentCompletionRes(
            String title,
            Instant completedAt
    ) {}

    public record QuestCoachContextRes(
            List<String> activeQuestTitles,
            List<RecentCompletionRes> recentCompletions,
            long activeQuestCount,
            long totalCompletedCount
    ) {}
}
