package com.questify.repository;

import com.questify.domain.Quest;
import com.questify.domain.QuestStatus;
import com.questify.domain.QuestVisibility;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface QuestRepository extends JpaRepository<Quest,Long> {

    Page<Quest> findByStatus(QuestStatus status, Pageable pageable);

    Page<Quest> findByCreatedByUserId(String userId, Pageable pageable);

    Page<Quest> findByTitleContainingIgnoreCase(String q, Pageable pageable);

    Page<Quest> findDistinctByCreatedByUserIdOrParticipantsUserId(
            String ownerId, String participantId, Pageable pageable);

    Page<Quest> findByVisibilityAndStatus(
            QuestVisibility visibility, QuestStatus status, Pageable pageable);

    @Query("""
           select distinct q
           from Quest q
           left join q.participants p
           where q.createdByUserId = :userId
              or p.userId = :userId
           """)
    Page<Quest> findMyOrParticipating(@Param("userId") String userId, Pageable pageable);

    @Query("""
           select q
           from Quest q
           where lower(q.title) like lower(concat('%', :q, '%'))
             and q.visibility = com.questify.domain.QuestVisibility.PUBLIC
             and q.status = com.questify.domain.QuestStatus.ACTIVE
           """)
    Page<Quest> searchPublic(@Param("q") String q, Pageable pageable);
}
