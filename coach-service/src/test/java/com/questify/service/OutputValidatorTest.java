package com.questify.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.questify.config.CoachProperties;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OutputValidatorTest {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    void validateSuccessPayload_returns_response_for_valid_json() {
        var validator = validator();
        var generatedAt = Instant.parse("2026-03-19T14:30:00Z");

        var response = validator.validateSuccessPayload(validPayload(), generatedAt);

        assertThat(response.status()).isEqualTo("SUCCESS");
        assertThat(response.source()).isEqualTo("AI");
        assertThat(response.model()).isEqualTo("smollm2:1.7b");
        assertThat(response.suggestions()).hasSize(3);
    }

    @Test
    void validateSuccessPayload_rejects_invalid_json() {
        var validator = validator();

        assertThatThrownBy(() -> validator.validateSuccessPayload("{not json", Instant.parse("2026-03-19T14:30:00Z")))
                .isInstanceOf(ModelOutputValidationException.class)
                .extracting(ex -> ((ModelOutputValidationException) ex).category())
                .isEqualTo("json_parse");
    }

    @Test
    void validateSuccessPayload_rejects_schema_failures() {
        var validator = validator();
        var generatedAt = Instant.parse("2026-03-19T14:30:00Z");
        String payload = """
                {
                  "suggestions": [],
                  "nudge": "Try again"
                }
                """;

        assertThatThrownBy(() -> validator.validateSuccessPayload(payload, generatedAt))
                .isInstanceOf(ModelOutputValidationException.class)
                .extracting(ex -> ((ModelOutputValidationException) ex).category())
                .isEqualTo("schema");
    }

    @Test
    void validateSuccessPayload_rejects_semantic_failures() {
        var validator = validator();
        var generatedAt = Instant.parse("2026-03-19T14:30:00Z");
        String payload = """
                {
                  "suggestions": [
                    {
                      "title": "Take a walk",
                      "estimatedMinutes": 15,
                      "difficulty": "easy",
                      "reason": "Good next step"
                    },
                    {
                      "title": "Stretch",
                      "estimatedMinutes": 10,
                      "difficulty": "easy",
                      "reason": "Low friction"
                    }
                  ],
                  "reflection": "Keep it small.",
                  "nudge": "Start with two minutes."
                }
                """;

        assertThatThrownBy(() -> validator.validateSuccessPayload(payload, generatedAt))
                .isInstanceOf(ModelOutputValidationException.class)
                .extracting(ex -> ((ModelOutputValidationException) ex).category())
                .isEqualTo("semantic");
    }

    @Test
    void validateSuccessPayload_ignores_server_owned_fields_from_model_output() {
        var validator = validator();
        var generatedAt = Instant.parse("2026-03-19T14:30:00Z");
        String payload = """
                {
                  "status": "SUCCESS",
                  "source": "AI",
                  "model": "wrong-model",
                  "generatedAt": "2025-01-01T00:00:00Z",
                  "suggestions": [
                    {
                      "title": "Take a 15-minute walk after dinner",
                      "estimatedMinutes": 15,
                      "difficulty": "easy",
                      "reason": "It matches your recent small wins and keeps momentum realistic."
                    },
                    {
                      "title": "Prepare tomorrow's workout clothes tonight",
                      "estimatedMinutes": 5,
                      "difficulty": "easy",
                      "reason": "This reduces friction and supports your exercise goal."
                    },
                    {
                      "title": "Do a short 10-minute stretching session",
                      "estimatedMinutes": 10,
                      "difficulty": "easy",
                      "reason": "This is a low-pressure way to stay consistent."
                    }
                  ],
                  "reflection": "You seem to do best with short, realistic actions. Keeping the next steps small helps maintain progress.",
                  "nudge": "If motivation is low, just commit to two minutes of movement."
                }
                """;

        var response = validator.validateSuccessPayload(payload, generatedAt);

        assertThat(response.model()).isEqualTo("smollm2:1.7b");
        assertThat(response.generatedAt()).isEqualTo(generatedAt);
    }

    private OutputValidator validator() {
        var properties = new CoachProperties();
        properties.setModel("smollm2:1.7b");
        properties.setSchemaVersion("v1");
        return new OutputValidator(objectMapper, properties, new PromptAssets(properties, objectMapper));
    }

    private static String validPayload() {
        return """
                {
                  "suggestions": [
                    {
                      "title": "Take a 15-minute walk after dinner",
                      "estimatedMinutes": 15,
                      "difficulty": "easy",
                      "reason": "It matches your recent small wins and keeps momentum realistic."
                    },
                    {
                      "title": "Prepare tomorrow's workout clothes tonight",
                      "estimatedMinutes": 5,
                      "difficulty": "easy",
                      "reason": "This reduces friction and supports your exercise goal."
                    },
                    {
                      "title": "Do a short 10-minute stretching session",
                      "estimatedMinutes": 10,
                      "difficulty": "easy",
                      "reason": "This is a low-pressure way to stay consistent."
                    }
                  ],
                  "reflection": "You seem to do best with short, realistic actions. Keeping the next steps small helps maintain progress.",
                  "nudge": "If motivation is low, just commit to two minutes of movement."
                }
                """;
    }
}
