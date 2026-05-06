package com.questify.repository;

import com.questify.domain.CreditedCompletion;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CreditedCompletionRepository extends JpaRepository<CreditedCompletion, Long> {
    boolean existsBySubmissionId(Long submissionId);
}
