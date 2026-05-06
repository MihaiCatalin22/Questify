package com.questify.controller;

import com.questify.service.QuestService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/quests")
@RequiredArgsConstructor
public class InternalAiReviewContextController {
    private final QuestService quests;

    @GetMapping("/{questId}/ai-review-context")
    public AiReviewQuestContextRes aiReviewContext(@PathVariable Long questId) {
        var quest = quests.get(questId);
        return new AiReviewQuestContextRes(quest.getTitle(), quest.getDescription());
    }

    public record AiReviewQuestContextRes(String title, String description) {}
}
