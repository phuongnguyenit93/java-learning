package com.example.projectbuild.executioncontext.model;

public record ExperimentContext(
        ExperimentExecution execution,
        ExecutionSourceContext sourceContext
) {
}
