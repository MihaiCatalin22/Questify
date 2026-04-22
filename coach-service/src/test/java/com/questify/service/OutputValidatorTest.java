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
                .isEqualTo("semantic");
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
    void validateSuccessPayload_accepts_top_level_array_payloads() {
        var validator = validator();
        var generatedAt = Instant.parse("2026-03-19T14:30:00Z");
        String payload = """
                [
                  {
                    "title": "Practice one restart segment",
                    "description": "Repeat one opener for a short drill and stop after the timer ends.",
                    "category": "HOBBY",
                    "estimatedMinutes": 15,
                    "difficulty": "easy",
                    "reason": "A short drill is easier to repeat than a full run."
                  },
                  {
                    "title": "Review one failed attempt",
                    "description": "Look at one recent mistake and write down the adjustment to test next.",
                    "category": "HOBBY",
                    "estimatedMinutes": 10,
                    "difficulty": "easy",
                    "reason": "A focused review makes the next attempt more specific."
                  }
                ]
                """;

        var response = validator.validateSuccessPayload(payload, generatedAt);

        assertThat(response.status()).isEqualTo("SUCCESS");
        assertThat(response.suggestions()).hasSize(2);
        assertThat(response.suggestions().getFirst().title()).isEqualTo("Practice one restart segment");
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

    @Test
    void validateSuccessPayload_salvages_alternative_top_level_keys_and_partial_suggestions() {
        var validator = validator();
        var generatedAt = Instant.parse("2026-03-19T14:30:00Z");
        String payload = """
                {
                  "recommendations": [
                    {
                      "task": "Walk after dinner",
                      "why": "Low effort way to keep momentum going."
                    },
                    {
                      "name": "Lay out workout clothes",
                      "details": "Get your setup ready tonight so tomorrow starts with less friction.",
                      "category": "routine"
                    }
                  ],
                  "summary": "Short actions fit your current pace."
                }
                """;

        var response = validator.validateSuccessPayload(payload, generatedAt);

        assertThat(response.suggestions()).hasSize(2);
        assertThat(response.suggestions().getFirst().title()).isEqualTo("Walk after dinner");
        assertThat(response.suggestions().getFirst().description()).contains("walk after dinner");
        assertThat(response.suggestions().getFirst().category()).isEqualTo("FITNESS");
        assertThat(response.suggestions().getFirst().estimatedMinutes()).isEqualTo(10);
        assertThat(response.suggestions().getFirst().difficulty()).isEqualTo("easy");
        assertThat(response.reflection()).isEqualTo("Short actions fit your current pace.");
        assertThat(response.nudge()).isNotBlank();
    }

    @Test
    void validateSuccessPayload_salvages_string_suggestions() {
        var validator = validator();
        var generatedAt = Instant.parse("2026-03-19T14:30:00Z");
        String payload = """
                {
                  "suggestions": [
                    "Take a 20 minute walk - easy way to stay consistent this week",
                    "Review your notes: capture one blocker and one next step"
                  ]
                }
                """;

        var response = validator.validateSuccessPayload(payload, generatedAt);

        assertThat(response.suggestions()).hasSize(2);
        assertThat(response.suggestions().getFirst().title()).isEqualTo("Take a 20 minute walk");
        assertThat(response.suggestions().getFirst().estimatedMinutes()).isEqualTo(20);
        assertThat(response.suggestions().getFirst().category()).isEqualTo("FITNESS");
        assertThat(response.reflection()).isNotBlank();
        assertThat(response.nudge()).isNotBlank();
    }

    @Test
    void validateSuccessPayload_salvages_plain_text_bullet_lists() {
        var validator = validator();
        var generatedAt = Instant.parse("2026-03-19T14:30:00Z");
        String payload = """
                1. Practice one restart segment - repeat one clean opener for 15 minutes.
                2. Review one failed attempt - note one mistake and one correction.
                3. Set up one clean restart routine - get your timer and notes ready.
                Reflection: Short focused drills build consistency faster than full runs.
                Nudge: Start with the easiest segment first.
                """;

        var response = validator.validateSuccessPayload(payload, generatedAt);

        assertThat(response.status()).isEqualTo("SUCCESS");
        assertThat(response.suggestions()).hasSize(3);
        assertThat(response.suggestions().getFirst().title()).isEqualTo("Practice one restart segment");
        assertThat(response.reflection()).isEqualTo("Short focused drills build consistency faster than full runs.");
        assertThat(response.nudge()).isEqualTo("Start with the easiest segment first.");
    }

    @Test
    void validateSuccessPayload_salvages_truncated_json_when_suggestions_array_is_complete() {
        var validator = validator();
        var generatedAt = Instant.parse("2026-03-19T14:30:00Z");
        String payload = """
                {
                  "suggestions": [
                    {
                      "title": "Review key math formulas",
                      "description": "Quickly review and memorize key math formulas for faster recall during study sessions.",
                      "category": "STUDY",
                      "estimatedMinutes": 15,
                      "difficulty": "easy",
                      "reason": "Memorizing core formulas makes later problem-solving easier."
                    },
                    {
                      "title": "History flashcard session",
                      "description": "Create and review history flashcards for important dates and events.",
                      "category": "STUDY",
                      "estimatedMinutes": 20,
                      "difficulty": "easy",
                      "reason": "Flashcards are a fast way to reinforce recall."
                    },
                    {
                      "title": "Math practice problems",
                      "description": "Solve a few targeted math problems from one weak area.",
                      "category": "STUDY",
                      "estimatedMinutes": 30,
                      "difficulty": "medium",
                      "reason": "Focused practice improves confidence in one topic at a time."
                    }
                  ],
                  "reflection": "It's important to
                """;

        var response = validator.validateSuccessPayload(payload, generatedAt);

        assertThat(response.status()).isEqualTo("SUCCESS");
        assertThat(response.suggestions()).hasSize(3);
        assertThat(response.suggestions().getFirst().title()).isEqualTo("Review key math formulas");
        assertThat(response.reflection()).isNotBlank();
        assertThat(response.nudge()).isNotBlank();
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
