package com.example.learning.oop.language_mechanics.exception;

import com.example.learning.oop.language_mechanics.controller.LanguageMechanicsExperimentsController;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Scoped error mapping avoids changing other OOP chapter controllers. */
@RestControllerAdvice(assignableTypes = LanguageMechanicsExperimentsController.class)
public final class LanguageMechanicsExceptionHandler {
    @ExceptionHandler({IllegalArgumentException.class, NullPointerException.class,
            ArithmeticException.class})
    public ResponseEntity<Map<String, String>> invalidInput(RuntimeException ex) {
        return ResponseEntity.badRequest().body(Map.of("error", "INVALID_INPUT"));
    }
}
