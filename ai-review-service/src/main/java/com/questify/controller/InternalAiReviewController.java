package com.questify.controller;

import com.questify.domain.AiReviewRunSource;
import com.questify.service.AiReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/internal/ai-reviews")
@RequiredArgsConstructor
public class InternalAiReviewController {
    private final AiReviewService reviews;

    @PostMapping("/submissions/{submissionId}/run")
    public Ack runForSubmission(@PathVariable Long submissionId) {
        try {
            var queued = reviews.queueRerunForSubmission(submissionId, AiReviewRunSource.FALLBACK_API, "submission-service");
            return new Ack("accepted", queued.getStatus().name(), queued.getSubmissionId());
        } catch (IllegalArgumentException notFound) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, notFound.getMessage(), notFound);
        }
    }

    public record Ack(String status, String runStatus, Long submissionId) {}
}
