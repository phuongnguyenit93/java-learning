package com.example.learning.oop.encapsulation.exception;

import com.example.learning.oop.encapsulation.controller.EncapsulationExperimentController;
import com.example.learning.oop.encapsulation.dto.EncapsulationDtos.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Scoped to this chapter; must not alter exception responses from other OOP controllers. */
@RestControllerAdvice(assignableTypes = EncapsulationExperimentController.class)
public final class EncapsulationExceptionHandler {
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> invalidInput(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(new ErrorResponse(exception.getMessage()));
    }

    @ExceptionHandler({IllegalStateException.class, ArithmeticException.class})
    public ResponseEntity<ErrorResponse> rejectedOperation(RuntimeException exception) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(new ErrorResponse(exception.getMessage()));
    }
}
