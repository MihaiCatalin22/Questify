package com.questify.dto;

import com.questify.config.validation.NoProfanity;
import com.questify.domain.QuestCategory;
import com.questify.domain.QuestStatus;
import com.questify.domain.QuestVisibility;
import jakarta.validation.constraints.*;

import java.time.Instant;

public class QuestDtos {

    public record CreateQuestReq(
            @NotBlank @Size(min = 3, max = 140) @NoProfanity String title,
            @NotBlank @Size(min = 10, max = 2000) @NoProfanity String description,
            @NotNull QuestCategory category,
            @NotNull Instant startDate,
            @NotNull Instant endDate,
            @NotNull QuestVisibility visibility,
            @NotNull Long createdByUserId
    ) {}

    public record UpdateQuestReq(
            @NotBlank @Size(min = 3, max = 140) @NoProfanity String title,
            @NotBlank @Size(min = 10, max = 2000) @NoProfanity String description,
            @NotNull QuestCategory category,
            Instant startDate,
            Instant endDate,
            @NotNull QuestVisibility visibility
    ) {}

    public record UpdateQuestStatusReq(
            @NotNull QuestStatus status
    ) {}

    public record QuestRes(
            Long id,
            String title,
            String description,
            QuestCategory category,
            QuestStatus status,
            Instant startDate,
            Instant endDate,
            Instant createdAt,
            Instant updatedAt,
            Long createdById,
            Integer participantsCount,
            Boolean completedByCurrentUser,
            QuestVisibility visibility
    ) {}
}
