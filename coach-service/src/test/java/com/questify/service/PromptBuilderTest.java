package com.questify.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.questify.config.CoachProperties;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PromptBuilderTest {

    @Test
    void buildPrimaryPrompt_renders_goal_history_and_server_owned_field_guidance() {
        var properties = properties();
        var assets = new PromptAssets(properties, new ObjectMapper());
        var builder = new PromptBuilder(assets, properties, new GoalFacetExtractor());
        var context = new CoachPromptContext(
                "Improve school grades with more math and history",
                List.of("Evening walk", "Stretch"),
                List.of(new CoachPromptContext.RecentCompletion("Morning run", Instant.parse("2026-03-01T08:00:00Z"))),
                2,
                7,
                true
        );

        var prompt = builder.buildPrimaryPrompt(
                context,
                com.questify.dto.CoachDtos.CoachSuggestionMode.DEFAULT,
                List.of("Take a 15-minute walk", "Prepare your next session")
        );

        assertThat(prompt.systemPrompt()).contains("You are Questify Coach.");
        assertThat(prompt.userPrompt()).contains("\"suggestions\": {");
        assertThat(prompt.userPrompt()).contains("Improve school grades with more math and history");
        assertThat(prompt.userPrompt()).contains("Goal facets:");
        assertThat(prompt.userPrompt()).contains("math");
        assertThat(prompt.userPrompt()).contains("history");
        assertThat(prompt.userPrompt()).contains("Spread the 3 suggestions across these sub-areas");
        assertThat(prompt.userPrompt()).contains("Morning run @ 2026-03-01T08:00:00Z");
        assertThat(prompt.userPrompt()).contains("Do not include status, source, model, or generatedAt.");
        assertThat(prompt.userPrompt()).contains("The server sets those fields.");
        assertThat(prompt.userPrompt()).contains("Keep every field concise and plain.");
        assertThat(prompt.userPrompt()).contains("description: one clear sentence the app can save directly");
        assertThat(prompt.userPrompt()).contains("category: one of COMMUNITY, FITNESS, HABIT, HOBBY, OTHER, STUDY, WORK");
        assertThat(prompt.userPrompt()).contains("Do not include startDate, endDate, visibility");
        assertThat(prompt.userPrompt()).contains("Keep nudge to 1 short sentence.");
        assertThat(prompt.userPrompt()).contains("do not quote, paraphrase, or reuse direct fragments of the user's goal text");
        assertThat(prompt.userPrompt()).contains("do not reuse any excluded suggestion title exactly");
        assertThat(prompt.userPrompt()).contains("Solve four algebra problems");
        assertThat(prompt.userPrompt()).contains("Practice one restart segment");
        assertThat(prompt.userPrompt()).contains("Take a 15-minute walk");
    }

    @Test
    void buildRepairPrompt_is_compact_and_uses_goal_facets_instead_of_replaying_the_full_prompt() {
        var properties = properties();
        var assets = new PromptAssets(properties, new ObjectMapper());
        var builder = new PromptBuilder(assets, properties, new GoalFacetExtractor());
        var context = new CoachPromptContext(
                "Improve school grades with more math and history",
                List.of("Algebra review", "History reading"),
                List.of(new CoachPromptContext.RecentCompletion("Math worksheet", Instant.parse("2026-03-01T08:00:00Z"))),
                2,
                7,
                true
        );

        var prompt = builder.buildRepairPrompt(
                context,
                com.questify.dto.CoachDtos.CoachSuggestionMode.DEFAULT,
                List.of("Solve four algebra problems"),
                "{\"bad\":true}",
                List.of("status must be SUCCESS")
        );

        assertThat(prompt.systemPrompt()).contains("Your previous output was invalid.");
        assertThat(prompt.systemPrompt()).contains("Do not reuse any excluded suggestion title exactly.");
        assertThat(prompt.systemPrompt()).contains("If the suggestions array is already valid, preserve it");
        assertThat(prompt.userPrompt()).contains("status must be SUCCESS");
        assertThat(prompt.userPrompt()).contains("Goal facets:");
        assertThat(prompt.userPrompt()).contains("math");
        assertThat(prompt.userPrompt()).contains("history");
        assertThat(prompt.userPrompt()).contains("Solve four algebra problems");
        assertThat(prompt.userPrompt()).contains("{\"bad\":true}");
        assertThat(prompt.userPrompt()).doesNotContain("Original prompt:");
    }

    private static CoachProperties properties() {
        var properties = new CoachProperties();
        properties.setModel("smollm2:1.7b");
        properties.setSchemaVersion("v1");
        return properties;
    }
}
