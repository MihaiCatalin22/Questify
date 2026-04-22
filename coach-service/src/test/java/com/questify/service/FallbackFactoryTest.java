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

    @Test
    void create_returns_game_practice_fallback_for_speedrunning_goals() {
        var factory = new FallbackFactory(Clock.fixed(Instant.parse("2026-03-19T14:31:12Z"), ZoneOffset.UTC));
        var context = new CoachPromptContext(
                "I want to practice speedrunning Grand Theft Auto V and improve my execution.",
                java.util.List.of(),
                java.util.List.of(),
                0,
                2,
                true
        );

        var response = factory.create(context, java.util.List.of());

        assertThat(response.suggestions()).hasSize(3);
        assertThat(response.suggestions()).extracting("category").containsOnly("HOBBY", "HABIT");
        assertThat(response.suggestions()).extracting("title")
                .contains("Practice one short route segment", "Review one failed attempt");
        assertThat(response.suggestions())
                .extracting(suggestion -> suggestion.description().toLowerCase())
                .noneMatch(description -> description.contains("walk"))
                .noneMatch(description -> description.contains("stretch"))
                .noneMatch(description -> description.contains("workout"));
    }
}
