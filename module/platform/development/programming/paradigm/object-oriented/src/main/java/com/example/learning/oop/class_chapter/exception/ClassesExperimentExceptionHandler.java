package com.example.learning.oop.class_chapter.exception;

import com.example.learning.oop.class_chapter.dto.ClassesExperimentDtos.ErrorResult;
import com.example.learning.oop.class_chapter.controller.ClassesExperimentController;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = ClassesExperimentController.class)
public final class ClassesExperimentExceptionHandler {
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResult> invalidInput(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(new ErrorResult(ex.getMessage()));
    }
}
