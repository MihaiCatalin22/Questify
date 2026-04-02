package com.questify.dto;

import java.time.Instant;
import java.util.List;

public final class CoachContextDtos {

    private CoachContextDtos() {}

    public record RecentCompletionRes(
            String title,
            Instant completedAt
    ) {}

    public record CoachContextRes(
            List<String> activeQuestTitles,
            List<RecentCompletionRes> recentCompletions,
            long activeQuestCount,
            long totalCompletedCount
    ) {}
}
