package com.example.learning.oop.oop_basic.exception;

import com.example.learning.oop.oop_basic.dto.BasicOopDtos.ErrorResponse;
import com.example.learning.oop.oop_basic.controller.BasicsExperimentController;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = BasicsExperimentController.class)
public final class BasicsExperimentExceptionHandler {
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> invalidInput(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(new ErrorResponse("INVALID_INPUT", ex.getMessage()));
    }
}
