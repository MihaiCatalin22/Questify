package com.questify.dto;

import com.questify.domain.ReviewStatus;
import jakarta.validation.constraints.*;

import java.time.Instant;

public class SubmissionDtos {
    public record CreateSubmissionReq(
            @NotNull Long questId,
            @NotBlank @Size(max=512) String proofKey,
            @Size(max=2000) String note
    ) {}

    public record ReviewReq(
            @NotNull ReviewStatus status,
            @Size(max=2000) String note // optional reviewer note
    ) {}

    public record SubmissionRes(
            Long id,
            Long questId,
            String userId,
            String proofKey,
            String note,
            ReviewStatus status,
            String reviewerUserId,
            Instant reviewedAt,
            Instant createdAt,
            Instant updatedAt
    ) {}
}
