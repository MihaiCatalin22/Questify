package com.questify.kafka;

import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ContainerProperties.AckMode;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;

@Configuration
public class KafkaConfig {

    @Bean
    DeadLetterPublishingRecoverer deadLetterPublishingRecoverer(KafkaTemplate<Object, Object> template) {
        return new DeadLetterPublishingRecoverer(
                template,
                (record, ex) -> new TopicPartition(record.topic() + ".dlq", record.partition())
        );
    }

    @Bean
    DefaultErrorHandler defaultErrorHandler(DeadLetterPublishingRecoverer dlpr) {
        var backoff = new ExponentialBackOffWithMaxRetries(5);
        backoff.setInitialInterval(1000L);
        backoff.setMultiplier(2.0);
        backoff.setMaxInterval(30_000L);

        var eh = new DefaultErrorHandler(dlpr, backoff);
        eh.setCommitRecovered(true);

        eh.addNotRetryableExceptions(
                org.springframework.security.access.AccessDeniedException.class,
                IllegalArgumentException.class
        );

        addNotRetryableIfPresent(eh, "jakarta.persistence.EntityNotFoundException");
        addNotRetryableIfPresent(eh, "org.springframework.dao.EmptyResultDataAccessException");

        return eh;
    }

    @SuppressWarnings("unchecked")
    private static void addNotRetryableIfPresent(DefaultErrorHandler eh, String className) {
        try {
            Class<? extends Exception> ex =
                    (Class<? extends Exception>) Class.forName(className);
            eh.addNotRetryableExceptions(ex);
        } catch (ClassNotFoundException ignored) {
        }
    }

    @Bean
    ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory(
            ConsumerFactory<String, Object> cf,
            DefaultErrorHandler eh
    ) {
        var f = new ConcurrentKafkaListenerContainerFactory<String, Object>();
        f.setConsumerFactory(cf);
        f.getContainerProperties().setAckMode(AckMode.MANUAL);
        f.setCommonErrorHandler(eh);
        return f;
    }
}
