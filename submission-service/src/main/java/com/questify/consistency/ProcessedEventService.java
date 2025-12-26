package com.questify.consistency;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class ProcessedEventService {

    private final ProcessedEventRepository processed;

    @Transactional
    public boolean markProcessedIfNew(String consumerGroup, String eventId) {
        if (eventId == null || eventId.isBlank()) {
            return true;
        }

        try {
            processed.save(new ProcessedEvent(new ProcessedEventId(consumerGroup, eventId), Instant.now()));
            return true;
        } catch (DataIntegrityViolationException dup) {
            return false;
        }
    }
}
