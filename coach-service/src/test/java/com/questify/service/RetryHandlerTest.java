package com.questify.service;

import com.questify.config.CoachProperties;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RetryHandlerTest {

    @Test
    void shouldRetry_only_for_first_validation_failure_when_enabled() {
        var properties = new CoachProperties();
        properties.setRetryEnabled(true);
        properties.setMaxRetries(1);
        var handler = new RetryHandler(properties);

        boolean allowed = handler.shouldRetry(1, new ModelOutputValidationException("schema", List.of("bad"), "{}"));
        boolean blocked = handler.shouldRetry(2, new ModelOutputValidationException("schema", List.of("bad"), "{}"));

        assertThat(allowed).isTrue();
        assertThat(blocked).isFalse();
    }

    @Test
    void shouldRetry_rejects_non_validation_failures() {
        var properties = new CoachProperties();
        properties.setRetryEnabled(true);
        properties.setMaxRetries(1);
        var handler = new RetryHandler(properties);

        assertThat(handler.shouldRetry(1, new IllegalStateException("boom"))).isFalse();
    }
}
