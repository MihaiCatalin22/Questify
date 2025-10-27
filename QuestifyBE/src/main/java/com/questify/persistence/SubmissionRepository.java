package com.questify.persistence;

import com.questify.domain.Quest;
import com.questify.domain.Submission;
import com.questify.domain.User;
import com.questify.domain.ReviewStatus;
import io.lettuce.core.dynamic.annotation.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface SubmissionRepository extends JpaRepository<Submission, Long> {
    Page<Submission> findByQuest(Quest quest, Pageable pageable);
    Page<Submission> findByUser(User user, Pageable pageable);
    Page<Submission> findByQuestAndReviewStatus(Quest quest, ReviewStatus reviewStatus, Pageable pageable);
    Page<Submission> findByReviewStatus(ReviewStatus status, Pageable pageable);
    boolean existsByQuest_IdAndUser_IdAndReviewStatus(Long questId, Long userId, ReviewStatus reviewStatus);

    @Query("""
           select s
           from Submission s
           where s.quest.id = :questId
             and s.user.id  = :userId
             and s.reviewStatus = com.questify.domain.ReviewStatus.APPROVED
           """)
    List<Submission> findApprovedByQuestAndUser(@Param("questId") Long questId,
                                                @Param("userId") Long userId);
}
