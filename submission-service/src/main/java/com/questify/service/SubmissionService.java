package com.questify.service;

import com.questify.client.ProofClient;
import com.questify.client.QuestAccessClient;
import com.questify.client.QuestProgressClient;
import com.questify.consistency.ProcessedEventService;
import com.questify.domain.ProofScanStatus;
import com.questify.domain.ReviewStatus;
import com.questify.domain.Submission;
import com.questify.domain.SubmissionProof;
import com.questify.dto.SubmissionDtos.CreateSubmissionReq;
import com.questify.dto.SubmissionDtos.ReviewReq;
import com.questify.kafka.EventPublisher;
import com.questify.repository.SubmissionProofRepository;
import com.questify.repository.SubmissionRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.*;

@Slf4j
@Service
public class SubmissionService {

    private final SubmissionRepository submissions;
    private final SubmissionProofRepository submissionProofs;
    private final QuestAccessClient questAccess;
    private final ProofClient proofClient;
    private final QuestProgressClient questProgress;
    private final EventPublisher events;
    private final ProcessedEventService processedEvents;

    @Value("${app.kafka.topics.submissions:submissions}")
    private String submissionsTopic;

    @Value("${app.kafka.topics.proofs:proofs}")
    private String proofsTopic;

    public SubmissionService(SubmissionRepository submissions,
                             SubmissionProofRepository submissionProofs,
                             QuestAccessClient questAccess,
                             ProofClient proofClient,
                             QuestProgressClient questProgress,
                             EventPublisher events,
                             ProcessedEventService processedEvents) {
        this.submissions = submissions;
        this.submissionProofs = submissionProofs;
        this.questAccess = questAccess;
        this.proofClient = proofClient;
        this.questProgress = questProgress;
        this.events = events;
        this.processedEvents = processedEvents;
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

        submissionProofs.save(SubmissionProof.builder()
                .submissionId(saved.getId())
                .proofKey(saved.getProofKey())
                .scanStatus(ProofScanStatus.PENDING)
                .build());

        publishProofUploaded(saved, saved.getProofKey());
        publishSubmissionCreated(saved);

        return saved;
    }

    @Transactional
    public Submission createFromMultipart(Long questId, String note, MultipartFile file, String userId, String bearer) {
        return createFromMultipartMany(questId, note, List.of(file), userId, bearer);
    }

    @Transactional
    public Submission createFromMultipartMany(Long questId, String note, List<MultipartFile> files, String userId, String bearer) {
        if (!questAccess.allowed(userId, questId)) {
            throw new AccessDeniedException("You are not a participant/owner of this quest.");
        }

        List<MultipartFile> safeFiles = (files == null) ? List.of() : files.stream()
                .filter(f -> f != null && !f.isEmpty())
                .toList();

        if (safeFiles.isEmpty()) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "At least one file is required");
        }

        if (safeFiles.size() > 10) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Too many files (max 10)");
        }

        List<String> uploadedKeys = new ArrayList<>();
        try {
            var firstUp = proofClient.upload(safeFiles.get(0), bearer);
            uploadedKeys.add(firstUp.key());

            var s = Submission.builder()
                    .questId(questId)
                    .userId(userId)
                    .proofKey(firstUp.key())
                    .note(note)
                    .status(ReviewStatus.SCANNING)
                    .build();

            var saved = submissions.save(s);

            submissionProofs.save(SubmissionProof.builder()
                    .submissionId(saved.getId())
                    .proofKey(firstUp.key())
                    .scanStatus(ProofScanStatus.PENDING)
                    .build());
            publishProofUploaded(saved, firstUp.key());

            for (int i = 1; i < safeFiles.size(); i++) {
                var up = proofClient.upload(safeFiles.get(i), bearer);
                uploadedKeys.add(up.key());

                submissionProofs.save(SubmissionProof.builder()
                        .submissionId(saved.getId())
                        .proofKey(up.key())
                        .scanStatus(ProofScanStatus.PENDING)
                        .build());

                publishProofUploaded(saved, up.key());
            }

            publishSubmissionCreated(saved);
            return saved;

        } catch (ResponseStatusException e) {
            cleanupUploaded(uploadedKeys);
            log.warn("Proof upload failed: status={} message={}", e.getStatusCode().value(), e.getReason());
            throw e;
        } catch (Exception e) {
            cleanupUploaded(uploadedKeys);
            log.error("Unexpected error during multi-proof upload", e);
            throw new ResponseStatusException(
                    org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR,
                    "Unexpected error during proof upload"
            );
        }
    }

    private void cleanupUploaded(List<String> keys) {
        for (String k : keys) {
            try {
                proofClient.deleteInternalObject(k);
            } catch (Exception ex) {
                log.warn("Failed to cleanup uploaded proof key={} err={}", k, ex.toString());
            }
        }
    }

    private void publishProofUploaded(Submission saved, String proofKey) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("submissionId", saved.getId());
        payload.put("questId", saved.getQuestId());
        payload.put("userId", saved.getUserId());
        payload.put("proofKey", proofKey);

        events.publish(
                proofsTopic,
                proofKey,
                "ProofUploaded", 1, "submission-service",
                payload
        );
    }

    private void publishSubmissionCreated(Submission saved) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("submissionId", saved.getId());
        payload.put("questId", saved.getQuestId());
        payload.put("userId", saved.getUserId());
        payload.put("status", saved.getStatus().name());
        if (notBlank(saved.getNote())) payload.put("note", saved.getNote());
        if (notBlank(saved.getProofKey())) payload.put("proofKey", saved.getProofKey()); // primary proof

        events.publish(
                submissionsTopic,
                String.valueOf(saved.getQuestId()),
                "SubmissionCreated", 1, "submission-service",
                payload
        );
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

    public Page<Submission> all(int page, int size) {
        return submissions.findAllByOrderByCreatedAtDesc(PageRequest.of(page, size));
    }

    @Transactional
    public Submission review(Long id, ReviewReq req, String reviewerUserId) {
        var s = get(id);

        if (req.status() == ReviewStatus.APPROVED && s.getStatus() == ReviewStatus.SCANNING) {
            throw new ResponseStatusException(
                    org.springframework.http.HttpStatus.CONFLICT,
                    "Cannot approve while proof is still scanning"
            );
        }

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

        if (req.status() == ReviewStatus.APPROVED) {
            questProgress.markCompleted(saved.getQuestId(), saved.getUserId(), saved.getId());
        }

        return saved;
    }

    @Transactional
    public void applyProofScanResultIdempotent(String consumerGroup, String eventId, String proofKey, String scanStatus) {
        if (!processedEvents.markProcessedIfNew(consumerGroup, eventId)) {
            log.info("Duplicate ProofScanned skipped eventId={} proofKey={}", eventId, proofKey);
            return;
        }
        applyProofScanResult(proofKey, scanStatus);
    }

    @Transactional
    public void applyProofScanResult(String proofKey, String scanStatus) {
        ProofScanStatus mapped = toScanStatus(scanStatus);

        var proofOpt = submissionProofs.findByProofKey(proofKey);

        if (proofOpt.isEmpty()) {
            var legacy = submissions.findByProofKey(proofKey);
            if (legacy.isEmpty()) {
                log.warn("proof-scanned for unknown proofKey={}, scanStatus={}", proofKey, scanStatus);
                return;
            }
            applyAggregateScanResultLegacy(legacy.get(), mapped);
            return;
        }

        var proof = proofOpt.get();
        proof.setScanStatus(mapped);
        proof.setScannedAt(Instant.now());
        submissionProofs.save(proof);

        var s = get(proof.getSubmissionId());

        if (s.getStatus() == ReviewStatus.APPROVED || s.getStatus() == ReviewStatus.REJECTED) {
            log.info("Ignoring proof-scanned for already-final submission id={} status={}", s.getId(), s.getStatus());
            return;
        }

        long total = submissionProofs.countBySubmissionId(s.getId());
        long bad = submissionProofs.countBySubmissionIdAndScanStatusIn(
                s.getId(), List.of(ProofScanStatus.INFECTED, ProofScanStatus.ERROR)
        );
        long clean = submissionProofs.countBySubmissionIdAndScanStatus(s.getId(), ProofScanStatus.CLEAN);

        if (bad > 0) {
            s.setStatus(ReviewStatus.REJECTED);
            s.setProofScanStatus(mapped == ProofScanStatus.CLEAN ? ProofScanStatus.ERROR : mapped);
            s.setProofScannedAt(Instant.now());

            String reason = "Proof scan failed: " + mapped;
            if (s.getNote() == null || s.getNote().isBlank()) {
                s.setNote(reason);
            } else if (!s.getNote().contains(reason)) {
                s.setNote(s.getNote() + "\n" + reason);
            }

            submissions.save(s);
            log.info("Submission id={} REJECTED (one or more proofs not clean)", s.getId());
            return;
        }

        if (total > 0 && clean == total) {
            if (s.getStatus() == ReviewStatus.SCANNING) {
                s.setStatus(ReviewStatus.PENDING);
            }
            s.setProofScanStatus(ProofScanStatus.CLEAN);
            s.setProofScannedAt(Instant.now());
            submissions.save(s);
            log.info("Submission id={} marked PENDING (all proofs CLEAN)", s.getId());
        }
    }

    private void applyAggregateScanResultLegacy(Submission s, ProofScanStatus mapped) {
        if (s.getStatus() == ReviewStatus.APPROVED || s.getStatus() == ReviewStatus.REJECTED) return;

        if (mapped == ProofScanStatus.CLEAN) {
            if (s.getStatus() == ReviewStatus.SCANNING) {
                s.setStatus(ReviewStatus.PENDING);
            }
            s.setProofScanStatus(ProofScanStatus.CLEAN);
            s.setProofScannedAt(Instant.now());
            submissions.save(s);
            return;
        }

        s.setStatus(ReviewStatus.REJECTED);
        s.setProofScanStatus(mapped);
        s.setProofScannedAt(Instant.now());

        String reason = "Proof scan failed: " + mapped;
        if (s.getNote() == null || s.getNote().isBlank()) {
            s.setNote(reason);
        } else if (!s.getNote().contains(reason)) {
            s.setNote(s.getNote() + "\n" + reason);
        }
        submissions.save(s);
    }

    private static ProofScanStatus toScanStatus(String raw) {
        if (raw == null) return ProofScanStatus.ERROR;
        String s = raw.trim().toUpperCase(Locale.ROOT);
        return switch (s) {
            case "CLEAN" -> ProofScanStatus.CLEAN;
            case "INFECTED" -> ProofScanStatus.INFECTED;
            case "ERROR", "FAILED", "FAIL" -> ProofScanStatus.ERROR;
            default -> ProofScanStatus.ERROR;
        };
    }

    public String signedGetUrl(String proofKey) { return proofClient.signGet(proofKey); }

    public List<String> proofKeysForSubmission(Long submissionId) {
        List<SubmissionProof> proofs = submissionProofs.findBySubmissionIdOrderByIdAsc(submissionId);
        if (proofs != null && !proofs.isEmpty()) {
            return proofs.stream().map(SubmissionProof::getProofKey).filter(Objects::nonNull).toList();
        }

        Submission s = get(submissionId);
        if (notBlank(s.getProofKey())) return List.of(s.getProofKey());
        return List.of();
    }

    public List<String> signedGetUrlsForSubmission(Long submissionId) {
        return proofKeysForSubmission(submissionId).stream()
                .map(this::signedGetUrl)
                .toList();
    }

    public String publicUrl(String proofKey) { return proofClient.publicUrl(proofKey); }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    public long countMine(String userId) {
        return submissions.countByUserId(userId);
    }
}
