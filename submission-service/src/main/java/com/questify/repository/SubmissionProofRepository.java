package com.questify.repository;

import com.questify.domain.ProofScanStatus;
import com.questify.domain.SubmissionProof;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SubmissionProofRepository extends JpaRepository<SubmissionProof, Long> {

    Optional<SubmissionProof> findByProofKey(String proofKey);

    List<SubmissionProof> findBySubmissionIdOrderByIdAsc(Long submissionId);

    long countBySubmissionId(Long submissionId);

    long countBySubmissionIdAndScanStatus(Long submissionId, ProofScanStatus status);

    long countBySubmissionIdAndScanStatusIn(Long submissionId, List<ProofScanStatus> statuses);
}
