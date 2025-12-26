package com.questify.repository;

import com.questify.domain.QuestCompletion;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuestCompletionRepository extends JpaRepository<QuestCompletion, Long> {
    long deleteByUserId(String userId);
}
