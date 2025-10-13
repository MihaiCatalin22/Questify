package com.questify.dto;

import com.questify.domain.ReviewStatus;
import jakarta.validation.constraints.*;
import java.time.Instant;

public class SubmissionDtos {
    public record CreateSubmissionReq(
       @NotNull Long questId,
       @NotNull Long userId,
       @Size(max = 2000) String proofText,
       @Size(max = 2000) @Pattern(regexp = "^(https?://).+", message = "The proof URL must start with http:// or https://") String proofUrl
    ) {}

    public record ReviewSubmissionReq(
            @NotNull ReviewStatus reviewStatus,
            @Size(max = 500) String reviewNote
    ) {}

    public record SubmissionRes(
            Long id,
            Long questId,
            Long userId,
            String proofText,
            String proofUrl,
            ReviewStatus reviewStatus,
            String reviewNote,
            Instant createdAt
    ) {}
}
