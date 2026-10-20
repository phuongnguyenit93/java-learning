package com.example.learning.oop.lifecycle.exception;

import com.example.learning.oop.lifecycle.controller.LifecycleExperimentController;
import com.example.learning.oop.lifecycle.dto.LifecycleDtos.ErrorResult;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = LifecycleExperimentController.class)
public final class LifecycleExperimentExceptionHandler {
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResult> invalidInput(IllegalArgumentException error) {
        return ResponseEntity.badRequest().body(new ErrorResult("INVALID_INPUT", error.getMessage()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResult> malformedJson(HttpMessageNotReadableException ignored) {
        return ResponseEntity.badRequest().body(new ErrorResult(
                "INVALID_JSON", "Request body must match the experiment input types"));
    }
}
