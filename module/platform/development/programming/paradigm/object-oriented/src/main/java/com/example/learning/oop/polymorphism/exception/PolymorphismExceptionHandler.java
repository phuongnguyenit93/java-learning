package com.example.learning.oop.polymorphism.exception;

import com.example.learning.oop.polymorphism.controller.PolymorphismExperimentsController;
import com.example.learning.oop.polymorphism.dto.PolymorphismDtos.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Chapter-scoped; must not change error handling of unrelated OOP controllers. */
@RestControllerAdvice(assignableTypes = PolymorphismExperimentsController.class)
public final class PolymorphismExceptionHandler {
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> invalid(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(new ErrorResponse(ex.getMessage()));
    }

    @ExceptionHandler({IllegalStateException.class, ArithmeticException.class})
    public ResponseEntity<ErrorResponse> rejected(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(new ErrorResponse(ex.getMessage()));
    }
}
