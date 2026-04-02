package com.questify.service;

import java.time.Instant;
import java.util.List;

public record CoachPromptContext(
        String goal,
        List<String> activeQuestTitles,
        List<RecentCompletion> recentCompletions,
        long activeQuestCount,
        long totalCompletedCount,
        boolean includeRecentHistory
) {
    public record RecentCompletion(
            String title,
            Instant completedAt
    ) {}
}
