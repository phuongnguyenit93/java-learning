package com.example.learning.oop.design_patterns.exception;

import com.example.learning.oop.design_patterns.controller.DesignPatternExperimentController;
import com.example.learning.oop.design_patterns.dto.DesignPatternDtos.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Applies only to the Design Patterns chapter, not unrelated OOP controllers. */
@RestControllerAdvice(assignableTypes = DesignPatternExperimentController.class)
public final class DesignPatternExceptionHandler {
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> invalid(IllegalArgumentException error) {
        return ResponseEntity.badRequest().body(new ErrorResponse(error.getMessage()));
    }

    @ExceptionHandler({IllegalStateException.class, ArithmeticException.class})
    public ResponseEntity<ErrorResponse> rejected(RuntimeException error) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(new ErrorResponse(error.getMessage()));
    }
}
