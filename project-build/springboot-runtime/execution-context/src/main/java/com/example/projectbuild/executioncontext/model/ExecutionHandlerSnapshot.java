package com.example.projectbuild.executioncontext.model;

public record ExecutionHandlerSnapshot(
        String controllerClass,
        String method,
        String signature
) {
}
