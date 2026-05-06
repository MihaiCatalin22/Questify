package com.questify.controller;

import com.questify.domain.AiReviewResult;
import com.questify.service.AiReviewService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/ai-reviews")
public class AiReviewController {
    private final AiReviewService reviews;

    public AiReviewController(AiReviewService reviews) {
        this.reviews = reviews;
    }

    @GetMapping("/submissions/{submissionId}")
    @PreAuthorize("hasAnyRole('REVIEWER','ADMIN')")
    public AiReviewRes bySubmission(@PathVariable Long submissionId) {
        AiReviewResult result = reviews.getForSubmission(submissionId);
        if (result == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "AI review is not ready yet");
        }
        return AiReviewRes.from(result);
    }

    public record AiReviewRes(
            Long submissionId,
            Long questId,
            String userId,
            String recommendation,
            double confidence,
            List<String> reasons,
            String modelName,
            boolean mediaSupported,
            Instant reviewedAt
    ) {
        static AiReviewRes from(AiReviewResult result) {
            return new AiReviewRes(
                    result.getSubmissionId(),
                    result.getQuestId(),
                    result.getUserId(),
                    result.getRecommendation().name(),
                    result.getConfidence(),
                    splitReasons(result.getReasons()),
                    result.getModel(),
                    result.isMediaSupported(),
                    result.getReviewedAt()
            );
        }

        private static List<String> splitReasons(String reasons) {
            if (reasons == null || reasons.isBlank()) {
                return List.of();
            }
            return Arrays.stream(reasons.split("\\r?\\n"))
                    .map(String::trim)
                    .filter(reason -> !reason.isBlank())
                    .toList();
        }
    }
}
