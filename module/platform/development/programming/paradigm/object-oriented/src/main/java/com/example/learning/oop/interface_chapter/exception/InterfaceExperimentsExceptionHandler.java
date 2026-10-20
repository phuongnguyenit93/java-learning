package com.example.learning.oop.interface_chapter.exception;

import com.example.learning.oop.interface_chapter.controller.InterfaceExperimentsController;
import com.example.learning.oop.interface_chapter.dto.InterfaceExperimentDtos.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Never intercepts exceptions for controllers owned by another OOP chapter. */
@RestControllerAdvice(assignableTypes = InterfaceExperimentsController.class)
public final class InterfaceExperimentsExceptionHandler {
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
