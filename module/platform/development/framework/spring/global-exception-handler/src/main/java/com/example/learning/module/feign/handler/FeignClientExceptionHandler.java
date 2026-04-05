package com.example.learning.module.feign.handler;

import com.example.learning.module.feign.exception.FeignClientException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class FeignClientExceptionHandler {
    @ExceptionHandler(FeignClientException.class)
    public ResponseEntity<Map<String, String>> handleFeignClient(FeignClientException ex) {
        return ResponseEntity.status(ex.getStatus())
                .body(Map.of("error", ex.getMessage()));
    }
}
