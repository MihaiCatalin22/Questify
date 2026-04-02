package com.questify.provider;

import com.questify.config.CoachProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ModelClientConfig {

    @Bean
    ModelClient modelClient(CoachProperties properties,
                            OllamaAdapter ollamaAdapter,
                            LlamaCppAdapter llamaCppAdapter) {
        return switch (properties.normalizedRuntime()) {
            case "ollama" -> ollamaAdapter;
            case "llamacpp", "llama.cpp" -> llamaCppAdapter;
            default -> throw new IllegalStateException("Unsupported coach runtime: " + properties.getRuntime());
        };
    }
}
