package com.questify.repository;

import com.questify.domain.QuestParticipant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface QuestParticipantRepository extends JpaRepository<QuestParticipant,Long> {
    Optional<QuestParticipant> findByQuest_IdAndUserId(Long questId, String userId);
    long countByQuest_Id(Long questId);
    List<QuestParticipant> findByQuest_Id(Long questId);
}
