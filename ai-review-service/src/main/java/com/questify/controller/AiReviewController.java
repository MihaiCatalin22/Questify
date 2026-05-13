package com.questify.controller;

import com.questify.domain.AiReviewResult;
import com.questify.domain.AiReviewRunSource;
import com.questify.service.AiReviewService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
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

    @PostMapping("/submissions/{submissionId}/run")
    @PreAuthorize("hasAnyRole('REVIEWER','ADMIN')")
    public AiReviewRes runForSubmission(@PathVariable Long submissionId, Authentication auth) {
        try {
            AiReviewResult result = reviews.rerunForSubmission(
                    submissionId,
                    AiReviewRunSource.MANUAL,
                    authUserId(auth)
            );
            return AiReviewRes.from(result);
        } catch (IllegalArgumentException notFound) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, notFound.getMessage(), notFound);
        }
    }

    private static String authUserId(Authentication auth) {
        if (auth == null) return "manual";
        Object principal = auth.getPrincipal();
        if (principal instanceof Jwt jwt) {
            String userId = jwt.getClaimAsString("user_id");
            if (userId != null && !userId.isBlank()) return userId;
            if (jwt.getSubject() != null && !jwt.getSubject().isBlank()) return jwt.getSubject();
        }
        return auth.getName() == null || auth.getName().isBlank() ? "manual" : auth.getName();
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
