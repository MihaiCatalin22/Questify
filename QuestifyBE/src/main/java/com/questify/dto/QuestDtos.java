package com.questify.dto;

import com.questify.config.validation.NoProfanity;
import com.questify.domain.QuestCategory;
import com.questify.domain.QuestStatus;
import jakarta.validation.constraints.*;

public class QuestDtos {
    public record CreateQuestReq(
            @NotBlank @Size(min = 3, max = 140) @NoProfanity String title,
            @NotBlank @Size(min = 10, max = 2000) @NoProfanity String description,
            @NotNull QuestCategory category,
            @NotNull Long createdByUserId
    ) {}

    public record UpdateQuestStatusReq(
            @NotNull QuestStatus status
    ) {}

    public record QuestRes(
            Long id,
            String title,
            String description,
            QuestStatus status,
            QuestCategory category,
            Long createdByUserId
    ) {}
}
