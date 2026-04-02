package com.questify.controller;

import com.questify.service.AiCoachOptInRequiredException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class RestExceptionHandler {

    record ApiError(String message) {}

    @ExceptionHandler(AiCoachOptInRequiredException.class)
    ResponseEntity<ApiError> handleOptInRequired(AiCoachOptInRequiredException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .header("Cache-Control", "no-store")
                .body(new ApiError(ex.getMessage()));
    }
}
