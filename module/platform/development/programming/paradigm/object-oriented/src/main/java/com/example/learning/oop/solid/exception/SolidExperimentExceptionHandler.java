package com.example.learning.oop.solid.exception;

import com.example.learning.oop.solid.controller.SolidExperimentController;
import com.example.learning.oop.solid.dto.SolidDtos.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = SolidExperimentController.class)
public final class SolidExperimentExceptionHandler {
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> invalid(IllegalArgumentException error) {
        return ResponseEntity.badRequest().body(new ErrorResponse("INVALID_INPUT", error.getMessage()));
    }

    @ExceptionHandler(PolicyContractException.class)
    public ResponseEntity<ErrorResponse> brokenPolicy(PolicyContractException error) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(new ErrorResponse("POLICY_CONTRACT", error.getMessage()));
    }

    @ExceptionHandler(ArithmeticException.class)
    public ResponseEntity<ErrorResponse> arithmetic(ArithmeticException error) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(new ErrorResponse("ARITHMETIC_OVERFLOW", "calculation exceeds supported long range"));
    }
}
