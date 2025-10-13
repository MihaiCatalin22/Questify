package com.questify.dto;

import com.questify.domain.QuestStatus;
import jakarta.validation.constraints.*;

public class QuestDtos {
    public record CreateQuestReq(
            @NotBlank @Size(min = 3, max = 140) String title,
            @NotBlank @Size(min = 10, max = 2000) String description,
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
            Long createdByUserId
    ) {}
}
