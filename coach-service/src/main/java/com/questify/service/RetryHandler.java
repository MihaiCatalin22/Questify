package com.questify.service;

import com.questify.config.CoachProperties;
import org.springframework.stereotype.Component;

@Component
public class RetryHandler {

    private final CoachProperties properties;

    public RetryHandler(CoachProperties properties) {
        this.properties = properties;
    }

    public boolean shouldRetry(int failedAttemptNumber, RuntimeException failure) {
        return properties.isRetryEnabled()
                && failedAttemptNumber <= properties.getMaxRetries()
                && failure instanceof ModelOutputValidationException;
    }
}
