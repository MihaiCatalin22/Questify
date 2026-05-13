package com.questify.dto;

import com.questify.domain.QuestCategory;
import com.questify.domain.QuestStatus;
import com.questify.domain.QuestVisibility;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;

public class QuestDtos {
    public record VerificationPolicyDto(
            List<@Size(min = 2, max = 120) String> requiredEvidence,
            List<@Size(min = 2, max = 120) String> optionalEvidence,
            List<@Size(min = 2, max = 120) String> disqualifiers,
            @DecimalMin("0.0") @DecimalMax("1.0") Double minSupportScore,
            @Size(min = 2, max = 80) String taskType
    ) {}

    public record CreateQuestReq(
            @NotBlank @Size(min = 3, max = 140) String title,
            @NotBlank @Size(min = 10, max = 2000) String description,
            @NotNull QuestCategory category,
            @NotNull Instant startDate,
            @NotNull Instant endDate,
            @NotNull QuestVisibility visibility,
            @NotNull String createdByUserId,
            VerificationPolicyDto verificationPolicy
    ) {}

    public record UpdateQuestReq(
            @NotBlank @Size(min = 3, max = 140) String title,
            @NotBlank @Size(min = 10, max = 2000) String description,
            @NotNull QuestCategory category,
            Instant startDate,
            Instant endDate,
            @NotNull QuestVisibility visibility,
            VerificationPolicyDto verificationPolicy
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
            String createdByUserId,
            Integer participantsCount,
            Boolean completedByCurrentUser,
            QuestVisibility visibility,
            VerificationPolicyDto verificationPolicy
    ) {}

}
