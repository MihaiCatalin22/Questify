package com.questify.provider;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Duration;

public record GenerationOptions(
        String model,
        Duration timeout,
        int maxOutputTokens,
        double temperature,
        JsonNode jsonSchema
) {}
