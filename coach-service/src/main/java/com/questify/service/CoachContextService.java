package com.questify.service;

import com.questify.client.QuestCoachContextClient;
import com.questify.client.UserCoachSettingsClient;
import com.questify.dto.CoachDtos.QuestCoachContextRes;
import com.questify.dto.CoachDtos.RecentCompletionRes;
import com.questify.dto.CoachDtos.UserCoachSettingsRes;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class CoachContextService {

    private static final int MAX_PROMPT_ITEMS = 5;
    private static final int MAX_TITLE_LENGTH = 120;
    private static final int MAX_GOAL_LENGTH = 500;

    private final UserCoachSettingsClient userCoachSettingsClient;
    private final QuestCoachContextClient questCoachContextClient;

    public CoachContextService(UserCoachSettingsClient userCoachSettingsClient,
                               QuestCoachContextClient questCoachContextClient) {
        this.userCoachSettingsClient = userCoachSettingsClient;
        this.questCoachContextClient = questCoachContextClient;
    }

    public CoachPromptContext loadContext(String userId, boolean includeRecentHistory) {
        UserCoachSettingsRes settings = userCoachSettingsClient.getCoachSettings(userId);
        if (!settings.aiCoachEnabled()) {
            throw new AiCoachOptInRequiredException("AI Coach opt-in is not enabled");
        }

        QuestCoachContextRes questContext = questCoachContextClient.getCoachContext(userId, includeRecentHistory);
        List<String> activeTitles = sanitizeTitles(questContext.activeQuestTitles());
        List<CoachPromptContext.RecentCompletion> recentCompletions = includeRecentHistory
                ? sanitizeCompletions(questContext.recentCompletions())
                : List.of();

        return new CoachPromptContext(
                resolveGoal(settings.coachGoal(), activeTitles),
                activeTitles,
                recentCompletions,
                Math.max(questContext.activeQuestCount(), 0),
                Math.max(questContext.totalCompletedCount(), 0),
                includeRecentHistory
        );
    }

    private List<String> sanitizeTitles(List<String> titles) {
        if (titles == null || titles.isEmpty()) {
            return List.of();
        }
        List<String> sanitized = new ArrayList<>();
        for (String title : titles) {
            String clean = sanitize(title, MAX_TITLE_LENGTH);
            if (!clean.isBlank()) {
                sanitized.add(clean);
            }
            if (sanitized.size() == MAX_PROMPT_ITEMS) {
                break;
            }
        }
        return List.copyOf(sanitized);
    }

    private List<CoachPromptContext.RecentCompletion> sanitizeCompletions(List<RecentCompletionRes> items) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        List<CoachPromptContext.RecentCompletion> sanitized = new ArrayList<>();
        for (RecentCompletionRes item : items) {
            if (item == null || item.completedAt() == null) {
                continue;
            }
            String cleanTitle = sanitize(item.title(), MAX_TITLE_LENGTH);
            if (cleanTitle.isBlank()) {
                continue;
            }
            sanitized.add(new CoachPromptContext.RecentCompletion(cleanTitle, item.completedAt()));
            if (sanitized.size() == MAX_PROMPT_ITEMS) {
                break;
            }
        }
        return List.copyOf(sanitized);
    }

    private String resolveGoal(String explicitGoal, List<String> activeQuestTitles) {
        String cleanedGoal = sanitize(explicitGoal, MAX_GOAL_LENGTH);
        if (!cleanedGoal.isBlank()) {
            return cleanedGoal;
        }
        if (!activeQuestTitles.isEmpty()) {
            return "Stay consistent with: " + String.join(", ", activeQuestTitles);
        }
        return "No explicit goal provided.";
    }

    private static String sanitize(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength);
    }
}
