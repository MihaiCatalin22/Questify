package com.questify.provider;

public class ModelClientException extends RuntimeException {
    public ModelClientException(String message) {
        super(message);
    }

    public ModelClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
