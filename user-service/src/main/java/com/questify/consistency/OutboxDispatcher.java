package com.questify.consistency;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.questify.kafka.EventEnvelope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxDispatcher {

    private final OutboxEventRepository outbox;
    private final KafkaTemplate<String, Object> kafka;
    private final ObjectMapper mapper;

    @Value("${app.outbox.enabled:true}")
    private boolean enabled;

    @Value("${app.outbox.batchSize:50}")
    private int batchSize;

    @Value("${app.outbox.maxAttempts:10}")
    private int maxAttempts;

    @Value("${app.outbox.baseRetrySeconds:5}")
    private int baseRetrySeconds;

    @Value("${app.outbox.sendTimeoutSeconds:10}")
    private int sendTimeoutSeconds;

    @Scheduled(fixedDelayString = "${app.outbox.dispatchDelayMs:1000}")
    public void dispatch() {
        if (!enabled) return;

        var now = Instant.now();
        var page = outbox.findByStatusAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
                OutboxStatus.NEW,
                now,
                PageRequest.of(0, batchSize, Sort.by("createdAt").ascending())
        );

        for (var ev : page.getContent()) {
            try {
                @SuppressWarnings("rawtypes")
                EventEnvelope env = mapper.readValue(ev.getEnvelopeJson(), EventEnvelope.class);

                kafka.send(ev.getTopic(), ev.getEventKey(), env)
                        .get(sendTimeoutSeconds, TimeUnit.SECONDS);

                ev.setStatus(OutboxStatus.SENT);
                ev.setSentAt(Instant.now());
                ev.setLastError(null);
                outbox.save(ev);
            } catch (Exception ex) {
                int nextAttempts = ev.getAttempts() + 1;
                ev.setAttempts(nextAttempts);
                ev.setLastError(ex.getClass().getSimpleName() + ": " + ex.getMessage());

                if (nextAttempts >= maxAttempts) {
                    ev.setStatus(OutboxStatus.FAILED);
                    log.error("Outbox FAILED id={} topic={} attempts={} error={}",
                            ev.getId(), ev.getTopic(), nextAttempts, ev.getLastError());
                } else {
                    ev.setNextAttemptAt(Instant.now().plusSeconds((long) nextAttempts * baseRetrySeconds));
                    log.warn("Outbox retry id={} topic={} attempt={} nextAttemptAt={} error={}",
                            ev.getId(), ev.getTopic(), nextAttempts, ev.getNextAttemptAt(), ev.getLastError());
                }

                outbox.save(ev);
            }
        }
    }
}
