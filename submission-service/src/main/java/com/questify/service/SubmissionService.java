package com.questify.service;

import com.questify.client.ProofClient;
import com.questify.client.QuestAccessClient;
import com.questify.client.QuestProgressClient;
import com.questify.domain.ReviewStatus;
import com.questify.domain.Submission;
import com.questify.kafka.EventPublisher;
import com.questify.dto.SubmissionDtos.CreateSubmissionReq;
import com.questify.dto.SubmissionDtos.ReviewReq;
import com.questify.repository.SubmissionRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Service
public class SubmissionService {

    private final SubmissionRepository submissions;
    private final QuestAccessClient questAccess;
    private final ProofClient proofClient;
    private final QuestProgressClient questProgress;
    private final EventPublisher events;

    @Value("${app.kafka.topics.submissions:submissions}")
    private String submissionsTopic;

    @Value("${app.kafka.topics.proofs:proofs}")
    private String proofsTopic;

    public SubmissionService(SubmissionRepository submissions,
                             QuestAccessClient questAccess,
                             ProofClient proofClient,
                             QuestProgressClient questProgress,
                             EventPublisher events) {
        this.submissions = submissions;
        this.questAccess = questAccess;
        this.proofClient = proofClient;
        this.questProgress = questProgress;
        this.events = events;
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
                .status(ReviewStatus.SCANNING)
                .build();
        var saved = submissions.save(s);

        // Build payload without nulls (note is optional)
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("submissionId", saved.getId());
        payload.put("questId", saved.getQuestId());
        payload.put("userId", saved.getUserId());
        payload.put("status", saved.getStatus().name());
        if (notBlank(saved.getNote())) payload.put("note", saved.getNote());
        if (notBlank(saved.getProofKey())) payload.put("proofKey", saved.getProofKey());

        events.publish(
                submissionsTopic,
                String.valueOf(saved.getQuestId()),
                "SubmissionCreated", 1, "submission-service",
                payload
        );

        return saved;
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
                    .status(ReviewStatus.SCANNING)
                    .build();
            var saved = submissions.save(s);

            // ProofUploaded event (all fields expected non-null, but build safely anyway)
            Map<String, Object> proofPayload = new LinkedHashMap<>();
            proofPayload.put("submissionId", saved.getId());
            proofPayload.put("questId", saved.getQuestId());
            proofPayload.put("userId", saved.getUserId());
            if (notBlank(saved.getProofKey())) proofPayload.put("proofKey", saved.getProofKey());

            events.publish(
                    proofsTopic,
                    saved.getProofKey(),
                    "ProofUploaded", 1, "submission-service",
                    proofPayload
            );

            // SubmissionCreated event (note is optional)
            Map<String, Object> submissionPayload = new LinkedHashMap<>();
            submissionPayload.put("submissionId", saved.getId());
            submissionPayload.put("questId", saved.getQuestId());
            submissionPayload.put("userId", saved.getUserId());
            submissionPayload.put("status", saved.getStatus().name());
            if (notBlank(saved.getNote())) submissionPayload.put("note", saved.getNote());
            if (notBlank(saved.getProofKey())) submissionPayload.put("proofKey", saved.getProofKey());

            events.publish(
                    submissionsTopic,
                    String.valueOf(saved.getQuestId()),
                    "SubmissionCreated", 1, "submission-service",
                    submissionPayload
            );

            return saved;
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

    public Page<Submission> byStatus(ReviewStatus status, int page, int size) {
        return submissions.findByStatus(status, PageRequest.of(page, size));
    }

    @Transactional
    public Submission review(Long id, ReviewReq req, String reviewerUserId) {
        var s = get(id);

        s.setStatus(req.status());
        s.setReviewerUserId(reviewerUserId);
        s.setReviewedAt(Instant.now());
        if (notBlank(req.note())) {
            s.setNote(req.note());
        }

        var saved = submissions.save(s);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("submissionId", saved.getId());
        payload.put("questId", saved.getQuestId());
        payload.put("userId", saved.getUserId());
        payload.put("reviewStatus", saved.getStatus().name());
        payload.put("reviewerId", reviewerUserId);

        events.publish(
                submissionsTopic,
                String.valueOf(saved.getQuestId()),
                "SubmissionReviewed", 1, "submission-service",
                payload
        );

        if (req.status() == ReviewStatus.APPROVED && s.getStatus() == ReviewStatus.SCANNING) {
            throw new ResponseStatusException(
                    org.springframework.http.HttpStatus.CONFLICT,
                    "Cannot approve while proof is still scanning"
            );
        }

        if (req.status() == ReviewStatus.APPROVED) {
            questProgress.markCompleted(saved.getQuestId(), saved.getUserId(), saved.getId());
        }

        return saved;
    }
    @Transactional
    public void applyProofScanResult(String proofKey, String scanStatus) {
        var opt = submissions.findByProofKey(proofKey);
        if (opt.isEmpty()) {
            log.warn("proof-scanned for unknown proofKey={}, scanStatus={}", proofKey, scanStatus);
            return;
        }

        var s = opt.get();

        if (s.getStatus() == ReviewStatus.APPROVED || s.getStatus() == ReviewStatus.REJECTED) {
            log.info("Ignoring proof-scanned for already-final submission id={} status={}", s.getId(), s.getStatus());
            return;
        }

        if ("CLEAN".equalsIgnoreCase(scanStatus)) {
            if (s.getStatus() == ReviewStatus.SCANNING) {
                s.setStatus(ReviewStatus.PENDING);
                submissions.save(s);
                log.info("Submission id={} proofKey={} marked PENDING (scan CLEAN)", s.getId(), proofKey);
            }
            return;
        }

        s.setStatus(ReviewStatus.REJECTED);

        String reason = "Proof scan failed: " + scanStatus;
        if (s.getNote() == null || s.getNote().isBlank()) {
            s.setNote(reason);
        } else if (!s.getNote().contains(reason)) {
            s.setNote(s.getNote() + "\n" + reason);
        }

        submissions.save(s);
        log.info("Submission id={} proofKey={} REJECTED (scanStatus={})", s.getId(), proofKey, scanStatus);
    }

    public String signedGetUrl(String proofKey) { return proofClient.signGet(proofKey); }
    public String publicUrl(String proofKey)    { return proofClient.publicUrl(proofKey); }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
