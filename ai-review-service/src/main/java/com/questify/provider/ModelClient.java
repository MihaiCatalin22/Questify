package com.questify.provider;

public interface ModelClient {
    ModelResponse generate(AiReviewPrompt prompt);

    record ModelResponse(String content, String modelUsed, boolean fallbackUsed, String fallbackReason) {}
}
