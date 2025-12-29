package com.questify.service;

import com.questify.client.ProofClient;
import com.questify.domain.ReviewStatus;
import com.questify.domain.SubmissionProof;
import com.questify.repository.SubmissionProofRepository;
import com.questify.repository.SubmissionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProofRetentionService {

    private final SubmissionRepository submissions;
    private final SubmissionProofRepository submissionProofs;
    private final ProofClient proofClient;

    @Value("${app.gdpr.proof-retention-days:30}")
    private int retentionDays;

    @Scheduled(fixedDelayString = "${app.gdpr.proof-retention-job-ms:3600000}")
    @Transactional
    public void run() {
        Instant cutoff = Instant.now().minus(retentionDays, ChronoUnit.DAYS);

        List<ReviewStatus> statuses = List.of(ReviewStatus.APPROVED, ReviewStatus.REJECTED);

        var targets = submissions.findByStatusInAndReviewedAtBeforeAndProofDeletedAtIsNull(statuses, cutoff);

        for (var s : targets) {
            List<String> keys = submissionProofs.findBySubmissionIdOrderByCreatedAtAsc(s.getId())
                    .stream()
                    .map(SubmissionProof::getProofKey)
                    .filter(k -> k != null && !k.isBlank())
                    .toList();

            if (keys.isEmpty() && s.getProofKey() != null && !s.getProofKey().isBlank()) {
                keys = List.of(s.getProofKey());
            }

            boolean ok = true;
            for (String key : keys) {
                try {
                    proofClient.deleteInternalObject(key);
                } catch (Exception e) {
                    ok = false;
                    log.warn("Retention delete failed: submissionId={} key={} err={}",
                            s.getId(), key, e.toString());
                }
            }

            if (ok) {
                s.setProofDeletedAt(Instant.now());
            }
        }
    }
}
