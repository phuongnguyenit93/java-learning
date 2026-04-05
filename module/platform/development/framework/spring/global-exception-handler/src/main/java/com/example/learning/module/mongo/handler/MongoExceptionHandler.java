package com.example.learning.module.mongo.handler;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class MongoExceptionHandler {
    @ExceptionHandler(DuplicateKeyException.class)
    @ConditionalOnClass(name = "org.springframework.dao.DuplicateKeyException")
    public ResponseEntity<Map<String, String>> handleDuplicateKey(DuplicateKeyException ex) {
        // Lấy message gốc "thô" nhất từ MongoDB Driver
        String rawMessage = ex.getMostSpecificCause().getMessage();

        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", rawMessage));
    }
}
