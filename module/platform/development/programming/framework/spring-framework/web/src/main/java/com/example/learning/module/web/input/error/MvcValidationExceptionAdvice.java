package com.example.learning.module.web.input.error;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

@RestControllerAdvice
public class MvcValidationExceptionAdvice {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleArgumentValidation(
            MethodArgumentNotValidException exception
    ) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "The request body was decoded, but object validation failed."
        );
        problem.setTitle("Request body validation failed");
        problem.setProperty("errorType", exception.getClass().getSimpleName());
        problem.setProperty("errorCount", exception.getBindingResult().getErrorCount());
        return ResponseEntity.badRequest().body(problem);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ProblemDetail> handleMethodValidation(
            HandlerMethodValidationException exception
    ) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "A direct controller method constraint failed before the handler method could complete."
        );
        problem.setTitle("Controller method validation failed");
        problem.setProperty("errorType", exception.getClass().getSimpleName());
        return ResponseEntity.badRequest().body(problem);
    }
}
