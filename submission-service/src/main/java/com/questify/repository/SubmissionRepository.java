package com.questify.repository;

import com.questify.domain.Submission;
import com.questify.domain.ReviewStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubmissionRepository extends JpaRepository<Submission, Long> {
    Page<Submission> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);
    Page<Submission> findByQuestIdOrderByCreatedAtDesc(Long questId, Pageable pageable);
    Page<Submission> findByStatus(ReviewStatus status, Pageable pageable);
}
