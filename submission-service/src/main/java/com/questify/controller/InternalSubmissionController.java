package com.questify.controller;

import com.questify.service.SubmissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/internal/submissions")
@RequiredArgsConstructor
public class InternalSubmissionController {
    private final SubmissionService submissions;

    @GetMapping("/{id}/ai-review-context")
    public AiReviewSubmissionContextRes aiReviewContext(@PathVariable Long id) {
        var submission = submissions.get(id);
        List<String> proofKeys = submissions.proofKeysForSubmission(id);
        return new AiReviewSubmissionContextRes(
                submission.getId(),
                submission.getQuestId(),
                submission.getUserId(),
                submission.getNote(),
                submission.getCreatedAt(),
                proofKeys
        );
    }

    public record AiReviewSubmissionContextRes(
            Long submissionId,
            Long questId,
            String userId,
            String note,
            Instant submittedAt,
            List<String> proofKeys
    ) {}
}
