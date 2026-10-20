package com.example.learning.oop.practice.exception;

import com.example.learning.oop.practice.controller.PracticeExperimentController;
import com.example.learning.oop.practice.dto.PracticeDtos.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.NoSuchElementException;

/** Scoped exclusively to this chapter, never other modules/controllers. */
@RestControllerAdvice(assignableTypes = PracticeExperimentController.class)
public final class PracticeExceptionHandler {
    @ExceptionHandler({IllegalArgumentException.class, NoSuchElementException.class})
    public ResponseEntity<ErrorResponse> invalid(RuntimeException ex) {
        return ResponseEntity.badRequest().body(new ErrorResponse(ex.getMessage()));
    }

    @ExceptionHandler({IllegalStateException.class, ArithmeticException.class})
    public ResponseEntity<ErrorResponse> rejected(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(new ErrorResponse(ex.getMessage()));
    }
}
