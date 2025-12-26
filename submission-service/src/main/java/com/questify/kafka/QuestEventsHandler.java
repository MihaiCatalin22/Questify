package com.questify.kafka;

import com.questify.consistency.ProcessedEventService;
import com.questify.domain.ReviewStatus;
import com.questify.repository.SubmissionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class QuestEventsHandler {

    private final SubmissionRepository submissions;
    private final ProcessedEventService processedEvents;

    @Value("${spring.application.name}")
    private String consumerGroup;

    @Transactional
    public void handle(EventEnvelope env) {
        if (env == null) return;

        if (!processedEvents.markProcessedIfNew(consumerGroup, env.eventId())) {
            log.info("Duplicate quest event skipped eventId={} type={}", env.eventId(), env.eventType());
            return;
        }

        if ("QuestArchived".equals(env.eventType())) {
            @SuppressWarnings("unchecked")
            var p = (Map<String, Object>) env.payload();
            Long questId = toLong(p.get("questId"));
            if (questId != null) {
                int affected = submissions.rejectAllPendingForQuest(
                        questId, ReviewStatus.PENDING, ReviewStatus.REJECTED,
                        "Quest archived – auto-rejected."
                );
                log.info("Auto-rejected {} pending submissions for archived quest {}", affected, questId);
            } else {
                log.warn("QuestArchived without questId payload={}", p);
            }
        }
    }

    private static Long toLong(Object o) {
        if (o == null) return null;
        if (o instanceof Number n) return n.longValue();
        try { return Long.parseLong(String.valueOf(o)); } catch (Exception e) { return null; }
    }
}
