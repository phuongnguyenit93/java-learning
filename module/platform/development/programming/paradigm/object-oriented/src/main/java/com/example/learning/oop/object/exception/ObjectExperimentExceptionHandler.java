package com.example.learning.oop.object.exception;

import com.example.learning.oop.object.dto.ObjectExperimentDto;
import com.example.learning.oop.object.controller.ObjectExperimentsController;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.http.HttpStatus;

@RestControllerAdvice(assignableTypes = ObjectExperimentsController.class)
public final class ObjectExperimentExceptionHandler {
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ObjectExperimentDto.ApiError> invalidInput(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ObjectExperimentDto.ApiError(ex.getMessage()));
    }
}
