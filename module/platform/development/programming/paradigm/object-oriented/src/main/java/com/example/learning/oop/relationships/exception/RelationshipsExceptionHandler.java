package com.example.learning.oop.relationships.exception;

import com.example.learning.oop.relationships.controller.RelationshipsExperimentsController;
import com.example.learning.oop.relationships.dto.RelationshipDtos.ApiError;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Error mappings apply to this chapter only; no global advice for other OOP APIs. */
@RestControllerAdvice(assignableTypes = RelationshipsExperimentsController.class)
public final class RelationshipsExceptionHandler {
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> invalidInput(IllegalArgumentException error) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiError("INVALID_INPUT", error.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiError> stateConflict(IllegalStateException error) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiError("STATE_CONFLICT", error.getMessage()));
    }

    @ExceptionHandler(ArithmeticException.class)
    public ResponseEntity<ApiError> arithmeticOverflow(ArithmeticException error) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(new ApiError("ARITHMETIC_OVERFLOW", "Calculation exceeded supported numeric limits"));
    }
}
