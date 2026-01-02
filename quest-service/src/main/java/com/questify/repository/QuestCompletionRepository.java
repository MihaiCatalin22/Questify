package com.questify.repository;

import com.questify.domain.QuestCompletion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface QuestCompletionRepository extends JpaRepository<QuestCompletion, Long> {
    Optional<QuestCompletion> findByQuestIdAndUserId(Long questId, String userId);
    boolean existsByQuestIdAndUserId(Long questId, String userId);
    long countByQuestId(Long questId);
    Optional<QuestCompletion> findBySubmissionId(Long submissionId);
    List<QuestCompletion> findByUserId(String userId);
    long deleteByUserId(String userId);
    @Query("""
       select count(c)
       from QuestCompletion c
       where c.userId = :userId
         and c.questId in (
           select distinct q.id
           from Quest q
           left join q.participants p
           where (q.createdByUserId = :userId or p.userId = :userId)
             and (
               :archived is null
               or (:archived = true and q.status = com.questify.domain.QuestStatus.ARCHIVED)
               or (:archived = false and q.status <> com.questify.domain.QuestStatus.ARCHIVED)
             )
         )
       """)
    long countMyCompletedFiltered(@Param("userId") String userId, @Param("archived") Boolean archived);

}
