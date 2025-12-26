package com.questify.kafka;

import com.questify.consistency.ProcessedEventService;
import com.questify.repository.QuestCompletionRepository;
import com.questify.repository.QuestParticipantRepository;
import com.questify.repository.QuestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserEventsHandler {

    private final ProcessedEventService processedEvents;
    private final QuestParticipantRepository participants;
    private final QuestCompletionRepository completions;
    private final QuestRepository quests;

    @Value("${spring.application.name}")
    private String consumerGroup;

    @Transactional
    public void handle(EventEnvelope env) {
        if (env == null) return;

        if (!"UserDeleted".equals(env.eventType())) {
            return;
        }

        if (!processedEvents.markProcessedIfNew(consumerGroup, env.eventId())) {
            log.info("Duplicate user event skipped eventId={} type={}", env.eventId(), env.eventType());
            return;
        }

        Object payloadObj = env.payload();
        if (!(payloadObj instanceof Map<?, ?> p)) {
            throw new IllegalArgumentException("UserDeleted payload must be a map, got: " +
                    (payloadObj == null ? "null" : payloadObj.getClass().getName()));
        }

        String userId = str(p.get("userId"));
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("Missing userId in UserDeleted payload: " + p);
        }

        long participantsDeleted = participants.deleteByUserId(userId);
        long completionsDeleted = completions.deleteByUserId(userId);

        String anon = "deleted-" + shortId(userId);
        int questsAnonymized = quests.anonymizeCreator(userId, anon);

        log.info(
                "GDPR cleanup (quest-service): userId={} participantsDeleted={} completionsDeleted={} questsAnonymized={} eventId={}",
                userId, participantsDeleted, completionsDeleted, questsAnonymized, env.eventId()
        );
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static String shortId(String userId) {
        if (userId == null) return "unknown";
        int n = Math.min(8, userId.length());
        return userId.substring(0, n);
    }
}
