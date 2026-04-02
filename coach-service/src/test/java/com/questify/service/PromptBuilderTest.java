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
        var builder = new PromptBuilder(assets, properties);
        var context = new CoachPromptContext(
                "Walk after dinner",
                List.of("Evening walk", "Stretch"),
                List.of(new CoachPromptContext.RecentCompletion("Morning run", Instant.parse("2026-03-01T08:00:00Z"))),
                2,
                7,
                true
        );

        var prompt = builder.buildPrimaryPrompt(context, com.questify.dto.CoachDtos.CoachSuggestionMode.DEFAULT);

        assertThat(prompt.systemPrompt()).contains("You are Questify Coach.");
        assertThat(prompt.userPrompt()).contains("\"suggestions\": {");
        assertThat(prompt.userPrompt()).contains("Walk after dinner");
        assertThat(prompt.userPrompt()).contains("Morning run @ 2026-03-01T08:00:00Z");
        assertThat(prompt.userPrompt()).contains("Do not include status, source, model, or generatedAt.");
        assertThat(prompt.userPrompt()).contains("The server sets those fields.");
    }

    @Test
    void buildRepairPrompt_includes_validation_errors_and_invalid_output() {
        var properties = properties();
        var assets = new PromptAssets(properties, new ObjectMapper());
        var builder = new PromptBuilder(assets, properties);
        var original = new com.questify.provider.GenerationPrompt("system", "original prompt");

        var prompt = builder.buildRepairPrompt(original, "{\"bad\":true}", List.of("status must be SUCCESS"));

        assertThat(prompt.systemPrompt()).contains("Your previous output was invalid.");
        assertThat(prompt.userPrompt()).contains("status must be SUCCESS");
        assertThat(prompt.userPrompt()).contains("original prompt");
        assertThat(prompt.userPrompt()).contains("{\"bad\":true}");
    }

    private static CoachProperties properties() {
        var properties = new CoachProperties();
        properties.setModel("smollm2:1.7b");
        properties.setSchemaVersion("v1");
        return properties;
    }
}
