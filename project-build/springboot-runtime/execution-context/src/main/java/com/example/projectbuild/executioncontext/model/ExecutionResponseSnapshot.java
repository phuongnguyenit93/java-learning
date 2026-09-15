package com.example.projectbuild.executioncontext.model;

public record ExecutionResponseSnapshot(
        int status,
        String contentType,
        String body
) {
}
