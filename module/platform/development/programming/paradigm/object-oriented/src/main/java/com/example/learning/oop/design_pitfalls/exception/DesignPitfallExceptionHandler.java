package com.example.learning.oop.design_pitfalls.exception;

import com.example.learning.oop.design_pitfalls.controller.DesignPitfallExperimentController;
import com.example.learning.oop.design_pitfalls.dto.DesignPitfallDtos.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Scoped solely to Design Pitfalls controller: does not affect other chapters. */
@RestControllerAdvice(assignableTypes = DesignPitfallExperimentController.class)
public final class DesignPitfallExceptionHandler {
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> invalidInput(IllegalArgumentException error) {
        return ResponseEntity.badRequest().body(new ErrorResponse(error.getMessage()));
    }

    @ExceptionHandler({IllegalStateException.class, ArithmeticException.class})
    public ResponseEntity<ErrorResponse> rejectedOperation(RuntimeException error) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(new ErrorResponse(error.getMessage()));
    }
}
