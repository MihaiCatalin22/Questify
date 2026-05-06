package com.questify.repository;

import com.questify.domain.AiReviewResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AiReviewResultRepository extends JpaRepository<AiReviewResult, Long> {
    Optional<AiReviewResult> findBySubmissionId(Long submissionId);
}
