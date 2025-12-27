package com.questify.service;

import com.questify.client.ProofClient;
import com.questify.domain.ReviewStatus;
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
    private final ProofClient proofClient;

    @Value("${app.gdpr.proof-retention-days:30}")
    private int retentionDays;

    @Scheduled(fixedDelayString = "${app.gdpr.proof-retention-job-ms:3600000}")
    @Transactional
    public void run() {
        Instant cutoff = Instant.now().minus(retentionDays, ChronoUnit.DAYS);

        var targets = submissions.findByStatusInAndReviewedAtBeforeAndProofDeletedAtIsNull(
                List.of(ReviewStatus.APPROVED, ReviewStatus.REJECTED),
                cutoff
        );

        for (var s : targets) {
            try {
                proofClient.deleteInternalObject(s.getProofKey());

                s.setProofDeletedAt(Instant.now());
            } catch (Exception e) {
                log.warn("Retention delete failed: submissionId={} key={} err={}",
                        s.getId(), s.getProofKey(), e.toString());
            }
        }
    }
}
