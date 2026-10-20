package com.example.learning.oop.testing.exception;

import com.example.learning.oop.testing.controller.TestingExperimentController;
import com.example.learning.oop.testing.dto.TestingDtos.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = TestingExperimentController.class)
public final class TestingExperimentExceptionHandler {
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> invalidInput(IllegalArgumentException error) {
        return ResponseEntity.badRequest().body(new ErrorResponse("INVALID_INPUT", error.getMessage()));
    }

    @ExceptionHandler(ArithmeticException.class)
    public ResponseEntity<ErrorResponse> arithmeticOverflow(ArithmeticException error) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(new ErrorResponse("ARITHMETIC_OVERFLOW", "the requested amounts overflow long"));
    }
}
