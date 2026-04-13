package com.example.learning.module.feign.handler;

import com.example.learning.module.feign.exception.FeignClientException;
import feign.FeignException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
@ConditionalOnClass(name = "feign.Feign")
public class FeignClientExceptionHandler {
    @ExceptionHandler(FeignClientException.class)
    public ResponseEntity<Map<String, String>> handleFeignClientException(FeignClientException ex) {
        return ResponseEntity.status(ex.getStatus())
                .body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(FeignException.class)
    public ResponseEntity<Map<String, String>> handleFeignException(FeignException ex) {
        return ResponseEntity.status(400)
                .body(Map.of("error", "Feign Exception Error : " + ex.getMessage()));
    }
}
