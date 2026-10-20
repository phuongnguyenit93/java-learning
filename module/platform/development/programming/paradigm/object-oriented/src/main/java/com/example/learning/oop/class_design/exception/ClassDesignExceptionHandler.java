package com.example.learning.oop.class_design.exception;

import com.example.learning.oop.class_design.controller.ClassDesignExperimentsController;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = ClassDesignExperimentsController.class)
public final class ClassDesignExceptionHandler {
    @ExceptionHandler({IllegalArgumentException.class, NullPointerException.class,
            ArithmeticException.class})
    public ResponseEntity<Map<String, String>> invalid(RuntimeException ex) {
        return ResponseEntity.badRequest().body(Map.of("error",
                ex.getMessage() == null ? "Invalid input" : ex.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> rejected(IllegalStateException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", ex.getMessage()));
    }
}
