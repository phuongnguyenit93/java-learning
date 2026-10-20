package com.example.learning.oop.abstraction.exception;

import com.example.learning.oop.abstraction.controller.AbstractionExperimentsController;
import com.example.learning.oop.abstraction.dto.AbstractionExperimentDto.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Scoped to this chapter: must not change error behavior of other workers' APIs. */
@RestControllerAdvice(assignableTypes = AbstractionExperimentsController.class)
public final class AbstractionExceptionHandler {
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> invalidArgument(IllegalArgumentException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("INVALID_INPUT", exception.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> invalidState(IllegalStateException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse("STATE_CONFLICT", exception.getMessage()));
    }

    @ExceptionHandler(ArithmeticException.class)
    public ResponseEntity<ErrorResponse> arithmeticOverflow(ArithmeticException exception) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(new ErrorResponse("ARITHMETIC_OVERFLOW", "Calculation exceeded supported numeric limits"));
    }
}
