package com.questify.service;

import com.questify.domain.QuestCompletion;
import com.questify.domain.QuestStatus;
import com.questify.kafka.EventPublisher;
import com.questify.repository.QuestCompletionRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Service
public class CompletionService {

    private final QuestCompletionRepository completions;
    private final EventPublisher events;

    @Value("${app.kafka.topics.streaks:streaks}")
    private String streaksTopic;

    public CompletionService(QuestCompletionRepository completions, EventPublisher events) {
        this.completions = completions;
        this.events = events;
    }

    @Transactional
    public QuestCompletion upsertCompleted(Long questId, String userId, Long submissionId) {
        var existing = completions.findByQuestIdAndUserId(questId, userId).orElse(null);
        QuestCompletion saved;

        if (existing == null) {
            var c = QuestCompletion.builder()
                    .questId(questId)
                    .userId(userId)
                    .submissionId(submissionId)
                    .status(QuestStatus.COMPLETED)
                    .completedAt(Instant.now())
                    .build();
            saved = completions.save(c);
        } else {
            existing.setStatus(QuestStatus.COMPLETED);
            if (submissionId != null) {
                existing.setSubmissionId(submissionId);
            }
            if (existing.getCompletedAt() == null) {
                existing.setCompletedAt(Instant.now());
            }
            saved = completions.save(existing);
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("questId", saved.getQuestId());
        payload.put("userId", saved.getUserId());
        payload.put("submissionId", saved.getSubmissionId());
        payload.put("completedAt", saved.getCompletedAt());

        events.publish(
                streaksTopic,
                String.valueOf(saved.getQuestId()),
                "QuestCompleted", 1, "quest-service",
                payload
        );

        return saved;
    }

    @Transactional(readOnly = true)
    public boolean isCompleted(Long questId, String userId) {
        return completions.existsByQuestIdAndUserId(questId, userId);
    }

    @Transactional(readOnly = true)
    public long countForQuest(Long questId) {
        return completions.countByQuestId(questId);
    }

    public long countCompletedInMineOrParticipatingFiltered(String userId, Boolean archived) {
        return completions.countMyCompletedFiltered(userId, archived);
    }

}
