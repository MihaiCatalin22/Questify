package com.questify.persistence;

import com.questify.domain.Quest;
import com.questify.domain.QuestStatus;
import com.questify.domain.QuestVisibility;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuestRepository extends JpaRepository<Quest,Long> {
    Page<Quest> findByStatus(QuestStatus status, Pageable pageable);
    Page<Quest> findByCreatedBy_Id(Long id, Pageable pageable);
    Page<Quest> findByTitleContainingIgnoreCase(String q, Pageable pageable);
    Page<Quest> findByCreatedBy_IdOrParticipants_Id(Long ownerId, Long participantId, Pageable pageable);
    Page<Quest> findByVisibilityAndStatus(QuestVisibility visibility, QuestStatus status, Pageable pageable);
}
