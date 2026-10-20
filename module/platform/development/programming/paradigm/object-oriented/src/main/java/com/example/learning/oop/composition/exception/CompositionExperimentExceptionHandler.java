package com.example.learning.oop.composition.exception;

import com.example.learning.oop.composition.controller.CompositionExperimentController;
import com.example.learning.oop.composition.dto.CompositionDtos.ErrorResult;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = CompositionExperimentController.class)
public final class CompositionExperimentExceptionHandler {
    @ExceptionHandler({IllegalArgumentException.class, ArithmeticException.class})
    public ResponseEntity<ErrorResult> invalidInput(RuntimeException ex) {
        return ResponseEntity.badRequest().body(new ErrorResult(
                "INVALID_INPUT", ex instanceof ArithmeticException
                        ? "Numeric total would overflow" : ex.getMessage()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResult> invalidJson(HttpMessageNotReadableException ignored) {
        return ResponseEntity.badRequest().body(new ErrorResult(
                "INVALID_JSON", "Request body does not match the declared input types"));
    }
}
