package com.questify.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.questify.config.CoachProperties;
import com.questify.dto.CoachDtos.CoachSuggestionRes;
import com.questify.dto.CoachDtos.CoachSuggestionsRes;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class OutputValidator {

    private static final Pattern FIRST_INTEGER = Pattern.compile("(\\d+)");
    private static final int MAX_SUGGESTIONS = 3;

    private final ObjectMapper objectMapper;
    private final CoachProperties properties;
    private final JsonSchema schema;

    public OutputValidator(ObjectMapper objectMapper, CoachProperties properties, PromptAssets assets) {
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.schema = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012)
                .getSchema(assets.schemaNode());
    }

    public CoachSuggestionsRes validateSuccessPayload(String rawOutput, java.time.Instant expectedGeneratedAt) {
        String trimmed = rawOutput == null ? "" : rawOutput.trim();
        if (trimmed.isEmpty()) {
            throw new ModelOutputValidationException("empty", List.of("Model output was empty"), rawOutput);
        }

        JsonNode tree = parse(trimmed, rawOutput);
        JsonNode modelPayload = normalizePayload(stripServerOwnedFields(tree, rawOutput), rawOutput);
        validateSchema(modelPayload, rawOutput);

        AiCoachPayload payload;
        try {
            payload = objectMapper.treeToValue(modelPayload, AiCoachPayload.class);
        } catch (JsonProcessingException ex) {
            throw new ModelOutputValidationException("mapping", List.of("Parsed JSON could not be mapped to response DTO"), rawOutput);
        }

        List<String> semanticErrors = new ArrayList<>();
        if (payload.suggestions() == null || payload.suggestions().isEmpty()) {
            semanticErrors.add("SUCCESS responses must contain at least 1 suggestion");
        }
        if (payload.suggestions() != null && payload.suggestions().size() > 3) {
            semanticErrors.add("SUCCESS responses must contain at most 3 suggestions");
        }

        if (!semanticErrors.isEmpty()) {
            throw new ModelOutputValidationException("semantic", semanticErrors, rawOutput);
        }
        return new CoachSuggestionsRes(
                "SUCCESS",
                "AI",
                properties.getModel(),
                expectedGeneratedAt,
                payload.suggestions(),
                payload.reflection(),
                payload.nudge()
        );
    }

    private JsonNode parse(String trimmed, String rawOutput) {
        try {
            return objectMapper.readTree(trimmed);
        } catch (JsonProcessingException ex) {
            String extracted = extractJsonObject(trimmed);
            if (extracted != null) {
                try {
                    return objectMapper.readTree(extracted);
                } catch (JsonProcessingException ignored) {
                    // Fall through to the original structured error.
                }
            }
            throw new ModelOutputValidationException("json_parse", List.of("Output was not valid JSON"), rawOutput);
        }
    }

    private String extractJsonObject(String rawText) {
        int start = rawText.indexOf('{');
        int end = rawText.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return null;
        }
        return rawText.substring(start, end + 1);
    }

    private JsonNode stripServerOwnedFields(JsonNode tree, String rawOutput) {
        if (!(tree instanceof ObjectNode objectNode)) {
            throw new ModelOutputValidationException("schema", List.of("Response must be a JSON object"), rawOutput);
        }
        ObjectNode sanitized = objectMapper.createObjectNode();
        copyFirstPresent(objectNode, sanitized, "suggestions", "suggestions", "quests", "recommendations", "items", "actions", "cards");
        copyFirstPresent(objectNode, sanitized, "reflection", "reflection", "summary", "insight", "pattern");
        copyFirstPresent(objectNode, sanitized, "nudge", "nudge", "nextStep", "next_step", "encouragement");
        return sanitized;
    }

    private JsonNode normalizePayload(JsonNode tree, String rawOutput) {
        if (!(tree instanceof ObjectNode objectNode)) {
            throw new ModelOutputValidationException("schema", List.of("Response must be a JSON object"), rawOutput);
        }

        ObjectNode normalized = objectMapper.createObjectNode();
        normalized.set("suggestions", normalizeSuggestions(objectNode.get("suggestions")));
        targetNormalizedText(objectNode, normalized, "reflection", synthesizeReflection(normalized.withArray("suggestions")));
        targetNormalizedText(objectNode, normalized, "nudge", synthesizeNudge(normalized.withArray("suggestions")));
        return normalized;
    }

    private ArrayNode normalizeSuggestions(JsonNode suggestionsNode) {
        ArrayNode normalizedSuggestions = objectMapper.createArrayNode();
        if (suggestionsNode == null || suggestionsNode.isNull()) {
            return normalizedSuggestions;
        }

        if (suggestionsNode.isArray()) {
            int count = 0;
            for (JsonNode suggestionNode : suggestionsNode) {
                if (count == MAX_SUGGESTIONS) {
                    break;
                }
                JsonNode normalizedSuggestion = normalizeSuggestion(suggestionNode);
                if (normalizedSuggestion != null) {
                    normalizedSuggestions.add(normalizedSuggestion);
                    count++;
                }
            }
            return normalizedSuggestions;
        }

        JsonNode normalizedSuggestion = normalizeSuggestion(suggestionsNode);
        if (normalizedSuggestion != null) {
            normalizedSuggestions.add(normalizedSuggestion);
        }
        return normalizedSuggestions;
    }

    private JsonNode normalizeSuggestion(JsonNode suggestionNode) {
        if (suggestionNode == null || suggestionNode.isNull()) {
            return null;
        }

        ObjectNode normalized = objectMapper.createObjectNode();
        if (suggestionNode.isTextual()) {
            normalizeTextSuggestion(suggestionNode.asText(), normalized);
            return normalized;
        }
        if (!(suggestionNode instanceof ObjectNode objectNode)) {
            return null;
        }

        String title = firstMeaningfulText(objectNode, "title", "name", "task", "action", "quest");
        String description = firstMeaningfulText(objectNode, "description", "details", "detail", "body", "summary");
        String reason = firstMeaningfulText(objectNode, "reason", "why", "rationale", "motivation");

        if (description.isBlank()) {
            description = synthesizeDescription(title, reason);
        }
        if (reason.isBlank()) {
            reason = synthesizeReason(title, description);
        }

        normalized.put("title", clampText(title, 80));
        normalized.put("description", clampText(description, 220));
        normalized.put("reason", clampText(reason, 120));
        normalized.put("category", inferOrNormalizeCategory(objectNode, title, description, reason));
        int estimatedMinutes = inferEstimatedMinutes(objectNode, title + " " + description + " " + reason);
        normalized.put("estimatedMinutes", estimatedMinutes);
        normalized.put("difficulty", inferOrNormalizeDifficulty(objectNode, estimatedMinutes));
        return normalized;
    }

    private void normalizeTextSuggestion(String rawText, ObjectNode target) {
        String trimmed = normalizeWhitespace(rawText);
        String title = trimmed;
        String description = trimmed;

        int separatorIndex = indexOfFirstSeparator(trimmed);
        if (separatorIndex > 0) {
            title = trimmed.substring(0, separatorIndex).trim();
            description = trimmed.substring(separatorIndex + 1).trim();
        }

        if (title.length() > 80) {
            title = title.substring(0, 80).trim();
        }
        if (description.length() < 10) {
            description = synthesizeDescription(title, "");
        }

        target.put("title", clampText(title, 80));
        target.put("description", clampText(description, 220));
        int estimatedMinutes = inferEstimatedMinutes(null, trimmed);
        target.put("estimatedMinutes", estimatedMinutes);
        target.put("difficulty", inferDifficultyFromMinutes(estimatedMinutes));
        target.put("category", inferCategory(title + " " + description));
        target.put("reason", clampText(synthesizeReason(title, description), 120));
    }

    private void targetNormalizedText(ObjectNode source, ObjectNode target, String fieldName, String fallback) {
        JsonNode value = source.get(fieldName);
        if (value != null && value.isTextual()) {
            target.put(fieldName, clampText(value.asText(), fieldName.equals("reflection") ? 180 : 120));
            return;
        }
        if (value != null && !value.isNull()) {
            target.put(fieldName, clampText(value.asText(), fieldName.equals("reflection") ? 180 : 120));
            return;
        }
        target.put(fieldName, clampText(fallback, fieldName.equals("reflection") ? 180 : 120));
    }

    private void copyFirstPresent(ObjectNode source, ObjectNode target, String targetField, String... sourceFields) {
        for (String field : sourceFields) {
            JsonNode value = source.get(field);
            if (value != null && !value.isNull()) {
                target.set(targetField, value);
                return;
            }
        }
    }

    private String inferOrNormalizeCategory(ObjectNode source, String title, String description, String reason) {
        JsonNode value = source.get("category");
        if (value == null || value.isNull()) {
            return inferCategory(title + " " + description + " " + reason);
        }
        if (!value.isTextual()) {
            return inferCategory(title + " " + description + " " + reason);
        }

        String normalized = switch (normalizeCategoryToken(value.asText())) {
            case "EXERCISE", "HEALTH", "WELLNESS" -> "FITNESS";
            case "ROUTINE", "ROUTINES", "CONSISTENCY" -> "HABIT";
            case "LEARNING", "LEARN" -> "STUDY";
            case "CAREER", "JOB", "PRODUCTIVITY" -> "WORK";
            case "CREATIVE", "CREATIVITY", "FUN", "LEISURE" -> "HOBBY";
            case "SOCIAL", "VOLUNTEER", "VOLUNTEERING" -> "COMMUNITY";
            case "GENERAL" -> "OTHER";
            default -> normalizeCategoryToken(value.asText());
        };
        return switch (normalized) {
            case "COMMUNITY", "FITNESS", "HABIT", "HOBBY", "OTHER", "STUDY", "WORK" -> normalized;
            default -> inferCategory(title + " " + description + " " + reason);
        };
    }

    private String inferOrNormalizeDifficulty(ObjectNode source, int estimatedMinutes) {
        JsonNode value = source.get("difficulty");
        if (value == null || value.isNull()) {
            return inferDifficultyFromMinutes(estimatedMinutes);
        }
        if (!value.isTextual()) {
            return inferDifficultyFromMinutes(estimatedMinutes);
        }

        return switch (value.asText().trim().toLowerCase(Locale.ROOT)) {
            case "beginner", "simple", "low" -> "easy";
            case "moderate", "normal", "intermediate" -> "medium";
            case "difficult", "challenging", "advanced" -> "hard";
            case "easy", "medium", "hard" -> value.asText().trim().toLowerCase(Locale.ROOT);
            default -> inferDifficultyFromMinutes(estimatedMinutes);
        };
    }

    private int inferEstimatedMinutes(ObjectNode source, String fallbackText) {
        JsonNode value = source == null ? null : source.get("estimatedMinutes");
        if (value == null || value.isNull()) {
            return inferMinutesFromText(fallbackText);
        }
        if (value.isIntegralNumber()) {
            return clampMinutes(value.asInt());
        }
        if (!value.isTextual()) {
            return inferMinutesFromText(fallbackText);
        }

        Matcher matcher = FIRST_INTEGER.matcher(value.asText());
        if (matcher.find()) {
            return clampMinutes(Integer.parseInt(matcher.group(1)));
        }
        return inferMinutesFromText(fallbackText);
    }

    private static String normalizeCategoryToken(String value) {
        return value.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
    }

    private static String firstMeaningfulText(ObjectNode source, String... fieldNames) {
        for (String fieldName : fieldNames) {
            JsonNode candidate = source.get(fieldName);
            if (candidate != null && !candidate.isNull()) {
                String text = normalizeWhitespace(candidate.asText());
                if (!text.isBlank()) {
                    return text;
                }
            }
        }
        return "";
    }

    private static String synthesizeDescription(String title, String reason) {
        String safeTitle = normalizeWhitespace(title);
        if (safeTitle.isBlank()) {
            safeTitle = "this quest";
        }
        String safeReason = normalizeWhitespace(reason);
        String generated = "Spend a short focused session on " + safeTitle.toLowerCase(Locale.ROOT) + ".";
        if (!safeReason.isBlank()) {
            generated += " " + safeReason;
        }
        return generated;
    }

    private static String synthesizeReason(String title, String description) {
        String source = !normalizeWhitespace(description).isBlank() ? description : title;
        if (source == null || source.isBlank()) {
            return "This is a realistic next step.";
        }
        return "This is a realistic next step based on: " + normalizeWhitespace(source);
    }

    private static String clampText(String value, int maxLength) {
        String normalized = normalizeWhitespace(value);
        if (normalized.isBlank()) {
            return maxLength > 20 ? "Small consistent actions help build momentum." : "Realistic next step.";
        }
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, maxLength).trim();
    }

    private static String normalizeWhitespace(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("\\s+", " ").trim();
    }

    private static int inferMinutesFromText(String text) {
        String normalized = normalizeWhitespace(text);
        Matcher matcher = FIRST_INTEGER.matcher(normalized);
        if (matcher.find()) {
            return clampMinutes(Integer.parseInt(matcher.group(1)));
        }
        String lower = normalized.toLowerCase(Locale.ROOT);
        if (lower.contains("quick") || lower.contains("short")) {
            return 10;
        }
        return 15;
    }

    private static int clampMinutes(int value) {
        return Math.max(1, Math.min(240, value));
    }

    private static String inferDifficultyFromMinutes(int estimatedMinutes) {
        if (estimatedMinutes <= 15) {
            return "easy";
        }
        if (estimatedMinutes <= 45) {
            return "medium";
        }
        return "hard";
    }

    private static String inferCategory(String text) {
        String normalized = normalizeWhitespace(text).toLowerCase(Locale.ROOT);
        if (containsAny(normalized, "walk", "run", "gym", "workout", "exercise", "fitness", "stretch", "cardio", "health")) {
            return "FITNESS";
        }
        if (containsAny(normalized, "study", "course", "exam", "learn", "reading", "read", "revise")) {
            return "STUDY";
        }
        if (containsAny(normalized, "project", "career", "code", "coding", "work", "job", "task", "productivity")) {
            return "WORK";
        }
        if (containsAny(normalized, "art", "draw", "music", "guitar", "write", "writing", "creative", "hobby")) {
            return "HOBBY";
        }
        if (containsAny(normalized, "friend", "family", "community", "volunteer", "social", "call someone")) {
            return "COMMUNITY";
        }
        return "HABIT";
    }

    private static boolean containsAny(String text, String... parts) {
        for (String part : parts) {
            if (text.contains(part)) {
                return true;
            }
        }
        return false;
    }

    private static int indexOfFirstSeparator(String value) {
        return List.of(" - ", ": ", ". ").stream()
                .map(value::indexOf)
                .filter(index -> index > 0)
                .min(Integer::compareTo)
                .orElse(-1);
    }

    private static String synthesizeReflection(ArrayNode suggestions) {
        if (!suggestions.isEmpty()) {
            JsonNode first = suggestions.get(0);
            String title = normalizeWhitespace(first.path("title").asText());
            if (!title.isBlank()) {
                return "The strongest next step is keeping the plan small and concrete, starting with " + title + ".";
            }
        }
        return "The best next move is a small action you can start right away.";
    }

    private static String synthesizeNudge(ArrayNode suggestions) {
        if (!suggestions.isEmpty()) {
            JsonNode first = suggestions.get(0);
            String title = normalizeWhitespace(first.path("title").asText());
            if (!title.isBlank()) {
                return "Start with " + title + " and keep the first session short.";
            }
        }
        return "Pick the easiest option and do five minutes now.";
    }

    private void validateSchema(JsonNode tree, String rawOutput) {
        List<String> errors = schema.validate(tree).stream()
                .map(message -> message.getMessage())
                .sorted(Comparator.naturalOrder())
                .toList();
        if (!errors.isEmpty()) {
            throw new ModelOutputValidationException("schema", errors, rawOutput);
        }
    }

    private record AiCoachPayload(
            List<CoachSuggestionRes> suggestions,
            String reflection,
            String nudge
    ) {}
}
