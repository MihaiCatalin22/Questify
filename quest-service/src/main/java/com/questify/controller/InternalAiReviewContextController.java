package com.questify.controller;

import com.questify.service.QuestService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/internal/quests")
@RequiredArgsConstructor
public class InternalAiReviewContextController {
    private final QuestService quests;

    @GetMapping("/{questId}/ai-review-context")
    public AiReviewQuestContextRes aiReviewContext(@PathVariable Long questId) {
        var quest = quests.get(questId);
        return new AiReviewQuestContextRes(
                quest.getTitle(),
                quest.getDescription(),
                quest.getVerificationRequiredEvidence() == null ? List.of() : List.copyOf(quest.getVerificationRequiredEvidence()),
                quest.getVerificationOptionalEvidence() == null ? List.of() : List.copyOf(quest.getVerificationOptionalEvidence()),
                quest.getVerificationDisqualifiers() == null ? List.of() : List.copyOf(quest.getVerificationDisqualifiers()),
                quest.getVerificationMinSupportScore(),
                quest.getVerificationTaskType()
        );
    }

    public record AiReviewQuestContextRes(
            String title,
            String description,
            List<String> requiredEvidence,
            List<String> optionalEvidence,
            List<String> disqualifiers,
            Double minSupportScore,
            String taskType
    ) {}
}
