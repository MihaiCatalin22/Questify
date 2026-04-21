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
                "I want to improve my school grades by doing more math and studying more history.",
                java.util.List.of("Evening walk"),
                java.util.List.of(),
                1,
                7,
                true
        );

        var response = factory.create(context, java.util.List.of("Do one focused study block"));

        assertThat(response.status()).isEqualTo("FALLBACK");
        assertThat(response.source()).isEqualTo("SYSTEM");
        assertThat(response.model()).isNull();
        assertThat(response.generatedAt()).isEqualTo(Instant.parse("2026-03-19T14:31:12Z"));
        assertThat(response.suggestions()).hasSize(3);
        assertThat(response.suggestions()).extracting("title").doesNotContain("Do one focused study block");
        assertThat(response.suggestions().getFirst().category()).isEqualTo("STUDY");
        assertThat(response.suggestions())
                .extracting(suggestion -> suggestion.description().toLowerCase())
                .noneMatch(description -> description.contains("school grades"))
                .noneMatch(description -> description.contains("math"))
                .noneMatch(description -> description.contains("history"));
    }
}
