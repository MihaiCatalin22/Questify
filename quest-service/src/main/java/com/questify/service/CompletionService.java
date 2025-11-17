package com.questify.service;

import com.questify.domain.QuestCompletion;
import com.questify.domain.QuestStatus;
import com.questify.repository.QuestCompletionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class CompletionService {

    private final QuestCompletionRepository completions;

    public CompletionService(QuestCompletionRepository completions) {
        this.completions = completions;
    }

    @Transactional
    public QuestCompletion upsertCompleted(Long questId, String userId, Long submissionId) {
        var existing = completions.findByQuestIdAndUserId(questId, userId).orElse(null);
        if (existing == null) {
            var c = QuestCompletion.builder()
                    .questId(questId)
                    .userId(userId)
                    .submissionId(submissionId)
                    .status(QuestStatus.COMPLETED)
                    .completedAt(Instant.now())
                    .build();
            return completions.save(c);
        }
        existing.setStatus(QuestStatus.COMPLETED);
        if (submissionId != null) existing.setSubmissionId(submissionId);
        return completions.save(existing);
    }

    @Transactional(readOnly = true)
    public boolean isCompleted(Long questId, String userId) {
        return completions.existsByQuestIdAndUserId(questId, userId);
    }

    @Transactional(readOnly = true)
    public long countForQuest(Long questId) {
        return completions.countByQuestId(questId);
    }
}
