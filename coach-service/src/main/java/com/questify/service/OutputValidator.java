package com.questify.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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

@Service
public class OutputValidator {

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
        JsonNode modelPayload = stripServerOwnedFields(tree, rawOutput);
        validateSchema(modelPayload, rawOutput);

        AiCoachPayload payload;
        try {
            payload = objectMapper.treeToValue(modelPayload, AiCoachPayload.class);
        } catch (JsonProcessingException ex) {
            throw new ModelOutputValidationException("mapping", List.of("Parsed JSON could not be mapped to response DTO"), rawOutput);
        }

        List<String> semanticErrors = new ArrayList<>();
        if (payload.suggestions() == null || payload.suggestions().size() != 3) {
            semanticErrors.add("SUCCESS responses must contain exactly 3 suggestions");
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
            throw new ModelOutputValidationException("json_parse", List.of("Output was not valid JSON"), rawOutput);
        }
    }

    private JsonNode stripServerOwnedFields(JsonNode tree, String rawOutput) {
        if (!(tree instanceof ObjectNode objectNode)) {
            throw new ModelOutputValidationException("schema", List.of("Response must be a JSON object"), rawOutput);
        }
        ObjectNode sanitized = objectNode.deepCopy();
        sanitized.remove("status");
        sanitized.remove("source");
        sanitized.remove("model");
        sanitized.remove("generatedAt");
        return sanitized;
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
