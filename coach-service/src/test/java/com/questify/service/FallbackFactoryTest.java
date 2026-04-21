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
        var context = new CoachPromptContext(
                "Walk daily",
                java.util.List.of("Evening walk"),
                java.util.List.of(),
                1,
                7,
                true
        );

        var response = factory.create(context);

        assertThat(response.status()).isEqualTo("FALLBACK");
        assertThat(response.source()).isEqualTo("SYSTEM");
        assertThat(response.model()).isNull();
        assertThat(response.generatedAt()).isEqualTo(Instant.parse("2026-03-19T14:31:12Z"));
        assertThat(response.suggestions()).hasSize(3);
        assertThat(response.suggestions().getFirst().title()).isEqualTo("Take one step toward Walk daily");
        assertThat(response.suggestions().getFirst().category()).isEqualTo("FITNESS");
    }
}
