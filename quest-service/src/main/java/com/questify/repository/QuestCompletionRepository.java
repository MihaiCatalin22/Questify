package com.questify.repository;

import com.questify.domain.QuestCompletion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface QuestCompletionRepository extends JpaRepository<QuestCompletion, Long> {
    Optional<QuestCompletion> findByQuestIdAndUserId(Long questId, String userId);
    boolean existsByQuestIdAndUserId(Long questId, String userId);
    long countByQuestId(Long questId);
    Optional<QuestCompletion> findBySubmissionId(Long submissionId);
    long deleteByUserId(String userId);
}
