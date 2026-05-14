package com.questify.provider;

import java.util.List;

public record AiReviewPrompt(String textPrompt, List<String> base64Images, Stage stage) {
    public AiReviewPrompt(String textPrompt, List<String> base64Images) {
        this(textPrompt, base64Images, Stage.GENERIC);
    }

    public enum Stage {
        OCR,
        OBSERVATION,
        CLAIM_CHECK,
        GENERIC
    }
}
