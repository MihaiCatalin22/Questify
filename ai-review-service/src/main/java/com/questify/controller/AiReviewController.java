package com.questify.controller;

import com.questify.domain.AiReviewResult;
import com.questify.domain.AiReviewRunSource;
import com.questify.service.AiReviewService;
import org.springframework.http.ResponseEntity;
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

    @GetMapping("/submissions/{submissionId}/status")
    @PreAuthorize("hasAnyRole('REVIEWER','ADMIN')")
    public RunStatusRes statusBySubmission(@PathVariable Long submissionId) {
        AiReviewResult result = reviews.getForSubmission(submissionId);
        if (result == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "AI review is not ready yet");
        }
        return new RunStatusRes(
                result.getSubmissionId(),
                result.getStatus() == null ? "COMPLETED" : result.getStatus().name(),
                result.getReviewedAt()
        );
    }

    @PostMapping("/submissions/{submissionId}/run")
    @PreAuthorize("hasAnyRole('REVIEWER','ADMIN')")
    public ResponseEntity<RunAcceptedRes> runForSubmission(@PathVariable Long submissionId, Authentication auth) {
        try {
            AiReviewResult result = reviews.queueRerunForSubmission(
                    submissionId,
                    AiReviewRunSource.MANUAL,
                    authUserId(auth)
            );
            return ResponseEntity.accepted().body(new RunAcceptedRes(
                    result.getSubmissionId(),
                    result.getStatus().name(),
                    "/ai-reviews/submissions/%d".formatted(result.getSubmissionId())
            ));
        } catch (IllegalArgumentException notFound) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, notFound.getMessage(), notFound);
        } catch (Exception queueError) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "AI review rerun failed. Please retry shortly; manual review remains available.",
                    queueError
            );
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
            String status,
            double confidence,
            double supportScore,
            List<String> reasons,
            String decisionNote,
            List<String> matchedEvidence,
            List<String> missingEvidence,
            List<String> matchedDisqualifiers,
            List<String> ocrSnippets,
            List<String> observedSignals,
            String decisionPath,
            boolean generatedPolicy,
            String modelUsed,
            boolean fallbackUsed,
            String fallbackReason,
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
                    result.getStatus() == null ? "COMPLETED" : result.getStatus().name(),
                    result.getConfidence(),
                    result.getSupportScore(),
                    splitReasons(result.getReasons()),
                    result.getDecisionNote(),
                    splitReasons(result.getMatchedEvidence()),
                    splitReasons(result.getMissingEvidence()),
                    splitReasons(result.getMatchedDisqualifiers()),
                    splitReasons(result.getOcrSnippets()),
                    splitReasons(result.getObservedSignals()),
                    result.getDecisionPath(),
                    result.isGeneratedPolicy(),
                    result.getModelUsed(),
                    result.isFallbackUsed(),
                    result.getFallbackReason(),
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

    public record RunAcceptedRes(
            Long submissionId,
            String status,
            String resultEndpoint
    ) {}

    public record RunStatusRes(
            Long submissionId,
            String status,
            Instant reviewedAt
    ) {}
}
