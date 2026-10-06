package com.example.projectbuild.exceptionhandler.servlet.feign;

import feign.FeignException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class FeignClientExceptionHandler {

    @ExceptionHandler(FeignClientException.class)
    public ResponseEntity<Map<String, String>> handleFeignClientException(FeignClientException ex) {
        return ResponseEntity.status(ex.getStatus())
                .body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(FeignException.class)
    public ResponseEntity<Map<String, String>> handleFeignException(FeignException ex) {
        return ResponseEntity.badRequest()
                .body(Map.of("error", "Feign Exception Error : " + ex.getMessage()));
    }
}
