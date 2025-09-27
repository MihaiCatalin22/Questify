package com.questify.persistence;

import com.questify.domain.Quest;
import com.questify.domain.Submission;
import com.questify.domain.User;
import com.questify.domain.ReviewStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubmissionRepository extends JpaRepository<Submission, Long> {
    Page<Submission> findByQuest(Quest quest, Pageable pageable);
    Page<Submission> findByUser(User user, Pageable pageable);
    Page<Submission> findByQuestAndReviewStatus(Quest quest, ReviewStatus reviewStatus, Pageable pageable);
}
