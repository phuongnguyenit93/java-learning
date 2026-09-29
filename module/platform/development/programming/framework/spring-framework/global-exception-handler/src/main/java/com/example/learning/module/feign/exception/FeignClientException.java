package com.example.learning.module.feign.exception;

import lombok.Getter;

@Getter
public class FeignClientException extends RuntimeException {
    private final int status;

    public FeignClientException(int status,String message) {
        super(message);
        this.status = status;
    }
}
