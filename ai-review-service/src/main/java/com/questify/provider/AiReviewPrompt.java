package com.questify.provider;

import java.util.List;

public record AiReviewPrompt(String textPrompt, List<String> base64Images) {}
