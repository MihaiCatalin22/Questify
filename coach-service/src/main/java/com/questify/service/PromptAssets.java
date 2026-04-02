package com.questify.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.questify.config.CoachProperties;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@Component
public class PromptAssets {

    private final String systemTemplate;
    private final String userTemplate;
    private final String repairTemplate;
    private final String schemaText;
    private final JsonNode schemaNode;

    public PromptAssets(CoachProperties properties, ObjectMapper objectMapper) {
        String version = properties.getSchemaVersion();
        this.systemTemplate = readResource("prompts/system-" + version + ".txt");
        this.userTemplate = readResource("prompts/user-" + version + ".txt");
        this.repairTemplate = readResource("prompts/repair-" + version + ".txt");
        this.schemaText = readResource("schemas/coach-response-" + version + ".json");
        try {
            this.schemaNode = objectMapper.readTree(schemaText);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to parse coach response schema", ex);
        }
    }

    public String systemTemplate() {
        return systemTemplate;
    }

    public String userTemplate() {
        return userTemplate;
    }

    public String repairTemplate() {
        return repairTemplate;
    }

    public String schemaText() {
        return schemaText;
    }

    public JsonNode schemaNode() {
        return schemaNode.deepCopy();
    }

    private static String readResource(String location) {
        var resource = new ClassPathResource(location);
        try (InputStream inputStream = resource.getInputStream()) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to load classpath resource: " + location, ex);
        }
    }
}
