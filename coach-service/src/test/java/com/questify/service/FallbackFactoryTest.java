package com.questify.service;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class FallbackFactoryTest {

    @Test
    void create_returns_safe_fallback_payload() {
        var factory = new FallbackFactory(Clock.fixed(Instant.parse("2026-03-19T14:31:12Z"), ZoneOffset.UTC));

        var response = factory.create();

        assertThat(response.status()).isEqualTo("FALLBACK");
        assertThat(response.source()).isEqualTo("SYSTEM");
        assertThat(response.model()).isNull();
        assertThat(response.generatedAt()).isEqualTo(Instant.parse("2026-03-19T14:31:12Z"));
        assertThat(response.suggestions()).isEmpty();
    }
}
