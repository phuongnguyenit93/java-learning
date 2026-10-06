package com.example.projectbuild.exceptionhandler.servlet.feign;

public class FeignClientException extends RuntimeException {
    private final int status;

    public FeignClientException(int status, String message) {
        super(message);
        this.status = status;
    }

    public int getStatus() {
        return status;
    }
}
