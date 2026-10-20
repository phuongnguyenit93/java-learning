package com.example.learning.oop.refactoring.exception;

import com.example.learning.oop.refactoring.controller.RefactoringExperimentsController;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = RefactoringExperimentsController.class)
public final class RefactoringExceptionHandler {
    @ExceptionHandler({IllegalArgumentException.class, NullPointerException.class,
            ArithmeticException.class})
    public ResponseEntity<Map<String, String>> invalidRequest(RuntimeException exception) {
        return ResponseEntity.badRequest().body(Map.of("error", "INVALID_INPUT"));
    }
}
