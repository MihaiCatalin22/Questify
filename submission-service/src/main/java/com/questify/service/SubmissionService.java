package com.questify.service;

import com.questify.client.ProofClient;
import com.questify.client.QuestAccessClient;
import com.questify.client.QuestProgressClient;
import com.questify.domain.ReviewStatus;
import com.questify.domain.Submission;
import com.questify.dto.SubmissionDtos.CreateSubmissionReq;
import com.questify.dto.SubmissionDtos.ReviewReq;
import com.questify.repository.SubmissionRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;

@Slf4j
@Service
public class SubmissionService {

    private final SubmissionRepository submissions;
    private final QuestAccessClient questAccess;
    private final ProofClient proofClient;
    private final QuestProgressClient questProgress;

    public SubmissionService(SubmissionRepository submissions,
                             QuestAccessClient questAccess,
                             ProofClient proofClient,
                             QuestProgressClient questProgress) {
        this.submissions = submissions;
        this.questAccess = questAccess;
        this.proofClient = proofClient;
        this.questProgress = questProgress;
    }

    @Transactional
    public Submission create(String userId, CreateSubmissionReq req) {
        if (!questAccess.allowed(userId, req.questId())) {
            throw new AccessDeniedException("You are not a participant/owner of this quest.");
        }
        var s = Submission.builder()
                .questId(req.questId())
                .userId(userId)
                .proofKey(req.proofKey())
                .note(req.note())
                .status(ReviewStatus.PENDING)
                .build();
        return submissions.save(s);
    }

    @Transactional
    public Submission createFromMultipart(Long questId, String note, MultipartFile file, String userId, String bearer) {
        if (!questAccess.allowed(userId, questId)) {
            throw new AccessDeniedException("You are not a participant/owner of this quest.");
        }

        try {
            var uploaded = proofClient.upload(file, bearer);
            var s = Submission.builder()
                    .questId(questId)
                    .userId(userId)
                    .proofKey(uploaded.key())
                    .note(note)
                    .status(ReviewStatus.PENDING)
                    .build();
            return submissions.save(s);
        } catch (ResponseStatusException e) {
            log.warn("Proof upload failed: status={} message={}", e.getStatusCode().value(), e.getReason());
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error during proof upload", e);
            throw new ResponseStatusException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR,
                    "Unexpected error during proof upload");
        }
    }

    public Submission get(Long id) {
        return submissions.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Submission %d not found".formatted(id)));
    }

    public Page<Submission> mine(String userId, int page, int size) {
        return submissions.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(page, size));
    }

    public Page<Submission> forQuest(Long questId, int page, int size) {
        return submissions.findByQuestIdOrderByCreatedAtDesc(questId, PageRequest.of(page, size));
    }

    public Page<Submission> pending(int page, int size) {
        return submissions.findByStatus(ReviewStatus.PENDING, PageRequest.of(page, size));
    }

    /** For reviewer list tabs: PENDING, APPROVED, REJECTED */
    public Page<Submission> byStatus(ReviewStatus status, int page, int size) {
        return submissions.findByStatus(status, PageRequest.of(page, size));
    }

    @Transactional
    public Submission review(Long id, ReviewReq req, String reviewerUserId) {
        var s = get(id);

        s.setStatus(req.status());
        s.setReviewerUserId(reviewerUserId);
        s.setReviewedAt(Instant.now());
        if (req.note() != null && !req.note().isBlank()) {
            s.setNote(req.note());
        }

        var saved = submissions.save(s);

        if (req.status() == ReviewStatus.APPROVED) {
            questProgress.markCompleted(saved.getQuestId(), saved.getUserId(), saved.getId());
        }

        return saved;
    }

    public String signedGetUrl(String proofKey) { return proofClient.signGet(proofKey); }
    public String publicUrl(String proofKey)    { return proofClient.publicUrl(proofKey); }
}
