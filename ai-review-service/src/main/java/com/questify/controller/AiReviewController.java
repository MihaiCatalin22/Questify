package com.questify.controller;

import com.questify.domain.AiReviewResult;
import com.questify.service.AiReviewService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;

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
            String recommendation,
            double confidence,
            String reasons,
            String model,
            boolean mediaSupported,
            Instant reviewedAt
    ) {
        static AiReviewRes from(AiReviewResult result) {
            return new AiReviewRes(
                    result.getSubmissionId(),
                    result.getRecommendation().name(),
                    result.getConfidence(),
                    result.getReasons(),
                    result.getModel(),
                    result.isMediaSupported(),
                    result.getReviewedAt()
            );
        }
    }
}
