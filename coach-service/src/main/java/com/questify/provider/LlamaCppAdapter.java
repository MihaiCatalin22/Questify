package com.questify.provider;

import org.springframework.stereotype.Component;

@Component
public class LlamaCppAdapter implements ModelClient {
    @Override
    public String generate(GenerationPrompt prompt, GenerationOptions options) {
        throw new ModelClientException("coach.runtime=llamacpp is configured, but the adapter is not implemented yet");
    }
}
