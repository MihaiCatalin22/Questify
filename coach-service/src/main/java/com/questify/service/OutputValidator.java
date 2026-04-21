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
        if (objectNode.has("suggestions")) {
            sanitized.set("suggestions", objectNode.get("suggestions"));
        }
        if (objectNode.has("reflection")) {
            sanitized.set("reflection", objectNode.get("reflection"));
        }
        if (objectNode.has("nudge")) {
            sanitized.set("nudge", objectNode.get("nudge"));
        }
        return sanitized;
    }

    private JsonNode normalizePayload(JsonNode tree, String rawOutput) {
        if (!(tree instanceof ObjectNode objectNode)) {
            throw new ModelOutputValidationException("schema", List.of("Response must be a JSON object"), rawOutput);
        }

        ObjectNode normalized = objectMapper.createObjectNode();
        normalized.set("suggestions", normalizeSuggestions(objectNode.get("suggestions")));
        copyNormalizedText(objectNode, normalized, "reflection");
        copyNormalizedText(objectNode, normalized, "nudge");
        return normalized;
    }

    private ArrayNode normalizeSuggestions(JsonNode suggestionsNode) {
        ArrayNode normalizedSuggestions = objectMapper.createArrayNode();
        if (suggestionsNode == null || suggestionsNode.isNull() || !suggestionsNode.isArray()) {
            return normalizedSuggestions;
        }

        int count = 0;
        for (JsonNode suggestionNode : suggestionsNode) {
            if (count == 3) {
                break;
            }
            normalizedSuggestions.add(normalizeSuggestion(suggestionNode));
            count++;
        }
        return normalizedSuggestions;
    }

    private JsonNode normalizeSuggestion(JsonNode suggestionNode) {
        if (!(suggestionNode instanceof ObjectNode objectNode)) {
            return suggestionNode;
        }

        ObjectNode normalized = objectMapper.createObjectNode();
        copyNormalizedText(objectNode, normalized, "title");
        copyNormalizedText(objectNode, normalized, "description");
        copyNormalizedCategory(objectNode, normalized);
        copyNormalizedEstimatedMinutes(objectNode, normalized);
        copyNormalizedDifficulty(objectNode, normalized);
        copyNormalizedText(objectNode, normalized, "reason");
        return normalized;
    }

    private void copyNormalizedText(ObjectNode source, ObjectNode target, String fieldName) {
        JsonNode value = source.get(fieldName);
        if (value == null || value.isNull()) {
            return;
        }
        if (value.isTextual()) {
            target.put(fieldName, value.asText().trim());
            return;
        }
        target.set(fieldName, value);
    }

    private void copyNormalizedCategory(ObjectNode source, ObjectNode target) {
        JsonNode value = source.get("category");
        if (value == null || value.isNull()) {
            return;
        }
        if (!value.isTextual()) {
            target.set("category", value);
            return;
        }

        String normalized = switch (value.asText().trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_')) {
            case "EXERCISE", "HEALTH", "WELLNESS" -> "FITNESS";
            case "ROUTINE", "ROUTINES", "CONSISTENCY" -> "HABIT";
            case "LEARNING", "LEARN" -> "STUDY";
            case "CAREER", "JOB", "PRODUCTIVITY" -> "WORK";
            case "CREATIVE", "CREATIVITY", "FUN", "LEISURE" -> "HOBBY";
            case "SOCIAL", "VOLUNTEER", "VOLUNTEERING" -> "COMMUNITY";
            case "GENERAL" -> "OTHER";
            default -> value.asText().trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        };
        target.put("category", normalized);
    }

    private void copyNormalizedDifficulty(ObjectNode source, ObjectNode target) {
        JsonNode value = source.get("difficulty");
        if (value == null || value.isNull()) {
            return;
        }
        if (!value.isTextual()) {
            target.set("difficulty", value);
            return;
        }

        String normalized = switch (value.asText().trim().toLowerCase(Locale.ROOT)) {
            case "beginner", "simple", "low" -> "easy";
            case "moderate", "normal", "intermediate" -> "medium";
            case "difficult", "challenging", "advanced" -> "hard";
            default -> value.asText().trim().toLowerCase(Locale.ROOT);
        };
        target.put("difficulty", normalized);
    }

    private void copyNormalizedEstimatedMinutes(ObjectNode source, ObjectNode target) {
        JsonNode value = source.get("estimatedMinutes");
        if (value == null || value.isNull()) {
            return;
        }
        if (value.isIntegralNumber()) {
            target.put("estimatedMinutes", value.asInt());
            return;
        }
        if (!value.isTextual()) {
            target.set("estimatedMinutes", value);
            return;
        }

        Matcher matcher = FIRST_INTEGER.matcher(value.asText());
        if (matcher.find()) {
            target.put("estimatedMinutes", Integer.parseInt(matcher.group(1)));
            return;
        }
        target.set("estimatedMinutes", value);
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
