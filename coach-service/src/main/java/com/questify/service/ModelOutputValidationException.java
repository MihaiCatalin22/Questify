package com.questify.service;

import java.util.List;

public class ModelOutputValidationException extends RuntimeException {

    private final String category;
    private final List<String> errors;
    private final String rawOutput;

    public ModelOutputValidationException(String category, List<String> errors, String rawOutput) {
        super(category + ": " + String.join("; ", errors));
        this.category = category;
        this.errors = List.copyOf(errors);
        this.rawOutput = rawOutput;
    }

    public String category() {
        return category;
    }

    public List<String> errors() {
        return errors;
    }

    public String rawOutput() {
        return rawOutput;
    }
}
