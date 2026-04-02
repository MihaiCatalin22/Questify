package com.questify.provider;

public class ModelTimeoutException extends ModelClientException {
    public ModelTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
}
