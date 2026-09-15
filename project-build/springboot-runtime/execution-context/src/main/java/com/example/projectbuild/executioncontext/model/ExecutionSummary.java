package com.example.projectbuild.executioncontext.model;

public record ExecutionSummary(
        String executionId,
        long startedAtEpochMilli,
        long completedAtEpochMilli,
        String httpMethod,
        String path,
        String controllerClass,
        String method,
        String signature,
        Integer status,
        long durationMillis,
        boolean failed,
        int logCount
) {
}
