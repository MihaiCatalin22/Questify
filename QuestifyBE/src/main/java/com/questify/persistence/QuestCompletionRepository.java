package com.questify.persistence;

import com.questify.domain.QuestCompletion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface QuestCompletionRepository extends JpaRepository<QuestCompletion, Long> {
    
    Optional<QuestCompletion> findByQuest_IdAndUser_Id(Long questId, Long userId);

    boolean existsByQuest_IdAndUser_IdAndStatus(Long questId, Long userId, QuestCompletion.CompletionStatus status);
}


