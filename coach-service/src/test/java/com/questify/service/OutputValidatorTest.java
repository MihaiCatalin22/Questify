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
    void validateSuccessPayload_accepts_between_one_and_three_suggestions() {
        var validator = validator();
        var generatedAt = Instant.parse("2026-03-19T14:30:00Z");
        String payload = """
                {
                  "suggestions": [
                    {
                      "title": "Take a walk",
                      "description": "Take a short walk to keep your routine moving forward this evening.",
                      "category": "FITNESS",
                      "estimatedMinutes": 15,
                      "difficulty": "easy",
                      "reason": "Good next step"
                    },
                    {
                      "title": "Stretch",
                      "description": "Do a quick full-body stretch session to stay loose without much effort.",
                      "category": "FITNESS",
                      "estimatedMinutes": 10,
                      "difficulty": "easy",
                      "reason": "Low friction"
                    }
                  ],
                  "reflection": "Keep it small.",
                  "nudge": "Start with two minutes."
                }
                """;

        var response = validator.validateSuccessPayload(payload, generatedAt);

        assertThat(response.suggestions()).hasSize(2);
        assertThat(response.status()).isEqualTo("SUCCESS");
    }

    @Test
    void validateSuccessPayload_truncates_more_than_three_suggestions() {
        var validator = validator();
        var generatedAt = Instant.parse("2026-03-19T14:30:00Z");
        String payload = """
                {
                  "suggestions": [
                    {
                      "title": "Walk",
                      "description": "Take a short walk after dinner to keep momentum going this week.",
                      "category": "FITNESS",
                      "estimatedMinutes": 15,
                      "difficulty": "easy",
                      "reason": "Low friction next step."
                    },
                    {
                      "title": "Stretch",
                      "description": "Do a short stretch routine to stay loose without a big time commitment.",
                      "category": "FITNESS",
                      "estimatedMinutes": 10,
                      "difficulty": "easy",
                      "reason": "Supports consistency."
                    },
                    {
                      "title": "Prep gear",
                      "description": "Lay out tomorrow's gear tonight so starting is easier in the morning.",
                      "category": "HABIT",
                      "estimatedMinutes": 5,
                      "difficulty": "easy",
                      "reason": "Reduces friction."
                    },
                    {
                      "title": "Log progress",
                      "description": "Write a short note about today's effort so you can track what is working.",
                      "category": "HABIT",
                      "estimatedMinutes": 5,
                      "difficulty": "easy",
                      "reason": "Keeps progress visible."
                    }
                  ],
                  "reflection": "Keep it small.",
                  "nudge": "Start with two minutes."
                }
                """;

        var response = validator.validateSuccessPayload(payload, generatedAt);

        assertThat(response.suggestions()).hasSize(3);
        assertThat(response.suggestions().get(2).title()).isEqualTo("Prep gear");
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
                      "description": "Go for a short walk after dinner to keep your movement goal realistic and consistent.",
                      "category": "FITNESS",
                      "estimatedMinutes": 15,
                      "difficulty": "easy",
                      "reason": "It matches your recent small wins and keeps momentum realistic."
                    },
                    {
                      "title": "Prepare tomorrow's workout clothes tonight",
                      "description": "Lay out your workout clothes tonight so tomorrow's session has less friction and setup time.",
                      "category": "HABIT",
                      "estimatedMinutes": 5,
                      "difficulty": "easy",
                      "reason": "This reduces friction and supports your exercise goal."
                    },
                    {
                      "title": "Do a short 10-minute stretching session",
                      "description": "Spend ten minutes on gentle stretching so you can stay active without overloading yourself.",
                      "category": "FITNESS",
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

    @Test
    void validateSuccessPayload_extracts_json_when_model_wraps_it_in_text() {
        var validator = validator();
        var generatedAt = Instant.parse("2026-03-19T14:30:00Z");
        String payload = """
                Sure thing! Here's the JSON:
                {
                  "suggestions": [
                    {
                      "title": "Take a 15-minute walk after dinner",
                      "description": "Go for a short walk after dinner to keep your movement goal realistic and consistent.",
                      "category": "FITNESS",
                      "estimatedMinutes": 15,
                      "difficulty": "easy",
                      "reason": "It matches your recent small wins and keeps momentum realistic."
                    }
                  ],
                  "reflection": "Short actions are working well for you.",
                  "nudge": "Start with the easiest win."
                }
                """;

        var response = validator.validateSuccessPayload(payload, generatedAt);

        assertThat(response.suggestions()).hasSize(1);
        assertThat(response.status()).isEqualTo("SUCCESS");
    }

    @Test
    void validateSuccessPayload_normalizes_common_model_drift() {
        var validator = validator();
        var generatedAt = Instant.parse("2026-03-19T14:30:00Z");
        String payload = """
                {
                  "status": "SUCCESS",
                  "source": "AI",
                  "model": "phi3:mini",
                  "generatedAt": "2026-03-19T14:29:00Z",
                  "suggestions": [
                    {
                      "title": "Walk",
                      "description": "Take a short walk after dinner to keep your momentum going this week.",
                      "category": "health",
                      "estimatedMinutes": "15 minutes",
                      "difficulty": "Beginner",
                      "reason": "Low friction.",
                      "extraField": "ignore this"
                    },
                    {
                      "title": "Stretch",
                      "description": "Do a quick stretch session so you stay active without a big time commitment.",
                      "category": "fitness",
                      "estimatedMinutes": 10,
                      "difficulty": "easy",
                      "reason": "Easy win."
                    },
                    {
                      "title": "Prep clothes",
                      "description": "Lay out tomorrow's clothes tonight so getting started feels easier tomorrow.",
                      "category": "routine",
                      "estimatedMinutes": "5",
                      "difficulty": "moderate",
                      "reason": "Reduces friction."
                    },
                    {
                      "title": "Extra quest",
                      "description": "This extra suggestion should be dropped so the payload still validates cleanly.",
                      "category": "OTHER",
                      "estimatedMinutes": 5,
                      "difficulty": "easy",
                      "reason": "Too many items."
                    }
                  ],
                  "reflection": "  Keep it small and repeatable.  ",
                  "nudge": "  Start with the easiest win.  ",
                  "extraTopLevel": "ignore this"
                }
                """;

        var response = validator.validateSuccessPayload(payload, generatedAt);

        assertThat(response.suggestions()).hasSize(3);
        assertThat(response.suggestions().getFirst().category()).isEqualTo("FITNESS");
        assertThat(response.suggestions().getFirst().estimatedMinutes()).isEqualTo(15);
        assertThat(response.suggestions().getFirst().difficulty()).isEqualTo("easy");
        assertThat(response.suggestions().get(2).category()).isEqualTo("HABIT");
        assertThat(response.suggestions().get(2).difficulty()).isEqualTo("medium");
        assertThat(response.reflection()).isEqualTo("Keep it small and repeatable.");
        assertThat(response.nudge()).isEqualTo("Start with the easiest win.");
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
                      "description": "Go for a short walk after dinner to keep your movement goal realistic and consistent.",
                      "category": "FITNESS",
                      "estimatedMinutes": 15,
                      "difficulty": "easy",
                      "reason": "It matches your recent small wins and keeps momentum realistic."
                    },
                    {
                      "title": "Prepare tomorrow's workout clothes tonight",
                      "description": "Lay out your workout clothes tonight so tomorrow's session has less friction and setup time.",
                      "category": "HABIT",
                      "estimatedMinutes": 5,
                      "difficulty": "easy",
                      "reason": "This reduces friction and supports your exercise goal."
                    },
                    {
                      "title": "Do a short 10-minute stretching session",
                      "description": "Spend ten minutes on gentle stretching so you can stay active without overloading yourself.",
                      "category": "FITNESS",
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
