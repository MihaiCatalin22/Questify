package com.questify.service;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class FallbackFactoryTest {

    @Test
    void create_returns_safe_fallback_payload() {
        var factory = new FallbackFactory(
                Clock.fixed(Instant.parse("2026-03-19T14:31:12Z"), ZoneOffset.UTC),
                new GoalFacetExtractor()
        );
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
        assertThat(response.suggestions()).extracting("category").containsOnly("STUDY");
        assertThat(response.suggestions()).extracting("title")
                .contains("Do one focused practice drill", "Review one recent attempt", "Prepare the next session");
        assertThat(response.suggestions())
                .extracting(suggestion -> suggestion.description().toLowerCase())
                .noneMatch(description -> description.contains("school grades"))
                .noneMatch(description -> description.contains("doing more math and studying more history"));
    }

    @Test
    void create_returns_game_practice_fallback_for_speedrunning_goals() {
        var factory = new FallbackFactory(
                Clock.fixed(Instant.parse("2026-03-19T14:31:12Z"), ZoneOffset.UTC),
                new GoalFacetExtractor()
        );
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
        assertThat(response.suggestions()).extracting("category").containsOnly("HOBBY");
        assertThat(response.suggestions()).extracting("title")
                .contains("Do one focused practice drill", "Review one recent attempt");
        assertThat(response.suggestions())
                .extracting(suggestion -> suggestion.description().toLowerCase())
                .noneMatch(description -> description.contains("grand theft auto"))
                .noneMatch(description -> description.contains("speedrunning grand theft auto v"));
    }

    @Test
    void create_returns_neutral_other_fallback_for_unclear_goals() {
        var factory = new FallbackFactory(
                Clock.fixed(Instant.parse("2026-03-19T14:31:12Z"), ZoneOffset.UTC),
                new GoalFacetExtractor()
        );
        var context = new CoachPromptContext(
                "I just want life to feel a bit less chaotic.",
                java.util.List.of(),
                java.util.List.of(),
                0,
                0,
                true
        );

        var response = factory.create(context, java.util.List.of("Do one focused practice drill"));

        assertThat(response.suggestions()).hasSize(3);
        assertThat(response.suggestions()).extracting("category").containsOnly("OTHER");
        assertThat(response.suggestions()).extracting("title").doesNotContain("Do one focused practice drill");
    }
}
