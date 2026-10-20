package com.example.learning.oop.subtyping.exception;

import com.example.learning.oop.subtyping.controller.SubtypingExperimentController;
import com.example.learning.oop.subtyping.dto.SubtypingDtos.ErrorResult;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = SubtypingExperimentController.class)
public final class SubtypingExperimentExceptionHandler {
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResult> invalidInput(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(new ErrorResult("INVALID_INPUT", ex.getMessage()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResult> invalidBody(HttpMessageNotReadableException ignored) {
        return ResponseEntity.badRequest().body(
                new ErrorResult("INVALID_JSON", "Request body must match the declared experiment types"));
    }
}
