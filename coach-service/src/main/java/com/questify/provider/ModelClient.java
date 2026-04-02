package com.questify.provider;

public interface ModelClient {
    String generate(GenerationPrompt prompt, GenerationOptions options);
}
