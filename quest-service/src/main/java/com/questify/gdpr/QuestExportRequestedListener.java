package com.questify.gdpr;

import com.questify.domain.Quest;
import com.questify.domain.QuestCompletion;
import com.questify.domain.QuestParticipant;
import com.questify.kafka.EventEnvelope;
import com.questify.repository.QuestCompletionRepository;
import com.questify.repository.QuestParticipantRepository;
import com.questify.repository.QuestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class QuestExportRequestedListener {

    private final QuestRepository quests;
    private final QuestParticipantRepository participants;
    private final QuestCompletionRepository completions;
    private final UserExportJobsClient exportJobs;

    @KafkaListener(
            topics = "${app.kafka.topics.users}",
            groupId = "${spring.application.name}-gdpr-export"
    )
    public void onEvent(EventEnvelope<?> env, Acknowledgment ack) {
        try {
            if (env == null || !"UserExportRequested".equals(env.eventType())) {
                ack.acknowledge();
                return;
            }

            if (!(env.payload() instanceof Map<?, ?> p)) {
                log.warn("UserExportRequested payload is not a map: {}", env.payload());
                ack.acknowledge();
                return;
            }

            String jobId = String.valueOf(p.get("jobId"));
            String userId = String.valueOf(p.get("userId"));

            List<Quest> owned = quests.findByCreatedByUserId(userId, Pageable.unpaged()).getContent();

            List<QuestParticipant> ps = participants.findByUserId(userId);
            Set<Long> participatingQuestIds = ps.stream()
                    .map(qp -> qp.getQuest().getId())
                    .collect(Collectors.toSet());

            List<Quest> participating = participatingQuestIds.isEmpty()
                    ? List.of()
                    : quests.findAllById(participatingQuestIds);

            List<QuestCompletion> cs = completions.findByUserId(userId);

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("userId", userId);

            payload.put("ownedQuests", owned.stream().map(q -> Map.of(
                    "id", q.getId(),
                    "title", q.getTitle(),
                    "description", q.getDescription(),
                    "category", q.getCategory(),
                    "visibility", q.getVisibility(),
                    "status", q.getStatus(),
                    "startDate", q.getStartDate(),
                    "endDate", q.getEndDate(),
                    "createdAt", q.getCreatedAt()
            )).toList());

            payload.put("participatingQuests", participating.stream().map(q -> Map.of(
                    "id", q.getId(),
                    "title", q.getTitle(),
                    "description", q.getDescription(),
                    "category", q.getCategory(),
                    "visibility", q.getVisibility(),
                    "status", q.getStatus(),
                    "startDate", q.getStartDate(),
                    "endDate", q.getEndDate(),
                    "createdAt", q.getCreatedAt()
            )).toList());

            payload.put("completions", cs.stream().map(c -> Map.of(
                    "questId", c.getQuestId(),
                    "submissionId", c.getSubmissionId(),
                    "status", c.getStatus(),
                    "completedAt", c.getCompletedAt()
            )).toList());

            exportJobs.uploadPart(jobId, "quest-service", payload);
            log.info("Uploaded quest-service export part jobId={} userId={}", jobId, userId);

            ack.acknowledge();
        } catch (Exception e) {
            log.error("Quest export listener failed; will retry", e);
        }
    }
}
