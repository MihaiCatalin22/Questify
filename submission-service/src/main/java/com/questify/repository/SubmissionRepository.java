package com.questify.repository;

import com.questify.domain.Submission;
import com.questify.domain.ReviewStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface SubmissionRepository extends JpaRepository<Submission, Long> {
    Page<Submission> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);
    Page<Submission> findByQuestIdOrderByCreatedAtDesc(Long questId, Pageable pageable);
    Page<Submission> findByStatus(ReviewStatus status, Pageable pageable);
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
           update Submission s
              set s.status = :rejected,
                  s.note = case when s.note is null or s.note = '' then :note else s.note end
            where s.questId = :questId
              and s.status = :pending
           """)
    int rejectAllPendingForQuest(@Param("questId") Long questId,
                                 @Param("pending") ReviewStatus pending,
                                 @Param("rejected") ReviewStatus rejected,
                                 @Param("note") String note);
    Optional<Submission> findByProofKey(String proofKey);
    long deleteByUserId(String userId);
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
           update Submission s
              set s.reviewerUserId = null
            where s.reviewerUserId = :userId
           """)
    int clearReviewerUserId(@Param("userId") String userId);
    long countByUserId(String userId);

}
