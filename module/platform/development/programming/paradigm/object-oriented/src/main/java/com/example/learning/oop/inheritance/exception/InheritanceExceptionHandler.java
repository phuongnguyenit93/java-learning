package com.example.learning.oop.inheritance.exception;

import com.example.learning.oop.inheritance.controller.InheritanceExperimentsController;
import com.example.learning.oop.inheritance.dto.InheritanceExperimentDto.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Error mappings are restricted to this chapter, never to other OOP controllers. */
@RestControllerAdvice(assignableTypes = InheritanceExperimentsController.class)
public final class InheritanceExceptionHandler {
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> badInput(IllegalArgumentException error) {
        return ResponseEntity.badRequest()
                .body(new ErrorResponse("INVALID_INPUT", error.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> invalidState(IllegalStateException error) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse("STATE_CONFLICT", error.getMessage()));
    }

    @ExceptionHandler(ArithmeticException.class)
    public ResponseEntity<ErrorResponse> calculationOverflow(ArithmeticException error) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(new ErrorResponse("ARITHMETIC_OVERFLOW", "Calculation exceeded supported numeric limits"));
    }
}
