package com.example.projectbuild.executioncontext.model;

public record ExecutionLogEntry(
        long timestamp,
        String level,
        String logger,
        String thread,
        String message
) {
}
