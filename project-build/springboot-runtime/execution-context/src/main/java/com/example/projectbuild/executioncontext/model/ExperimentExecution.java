package com.example.projectbuild.executioncontext.model;

import java.util.ArrayList;
import java.util.List;

public record ExperimentExecution(
        String executionId,
        ExecutionHandlerSnapshot handler,
        ExecutionRequestSnapshot request,
        ExecutionResponseSnapshot response,
        List<ExecutionLogEntry> logs,
        ExecutionExceptionSnapshot exception,
        long startedAtEpochMilli,
        long completedAtEpochMilli,
        long durationMillis
) {

    public ExperimentExecution {
        logs = logs == null ? List.of() : List.copyOf(logs);
    }

    public static ExperimentExecution started(
            String executionId,
            ExecutionRequestSnapshot request,
            long startedAtEpochMilli
    ) {
        return new ExperimentExecution(
                executionId,
                null,
                request,
                null,
                List.of(),
                null,
                startedAtEpochMilli,
                0,
                0
        );
    }

    public ExperimentExecution withHandler(ExecutionHandlerSnapshot handler) {
        return new ExperimentExecution(
                executionId,
                handler,
                request,
                response,
                logs,
                exception,
                startedAtEpochMilli,
                completedAtEpochMilli,
                durationMillis
        );
    }

    public ExperimentExecution withRequest(ExecutionRequestSnapshot request) {
        return new ExperimentExecution(
                executionId,
                handler,
                request,
                response,
                logs,
                exception,
                startedAtEpochMilli,
                completedAtEpochMilli,
                durationMillis
        );
    }

    public ExperimentExecution withLog(ExecutionLogEntry logEntry) {
        List<ExecutionLogEntry> updatedLogs = new ArrayList<>(logs);
        updatedLogs.add(logEntry);
        return new ExperimentExecution(
                executionId,
                handler,
                request,
                response,
                updatedLogs,
                exception,
                startedAtEpochMilli,
                completedAtEpochMilli,
                durationMillis
        );
    }

    public ExperimentExecution completed(
            ExecutionResponseSnapshot response,
            long completedAtEpochMilli
    ) {
        return new ExperimentExecution(
                executionId,
                handler,
                request,
                response,
                logs,
                exception,
                startedAtEpochMilli,
                completedAtEpochMilli,
                Math.max(0, completedAtEpochMilli - startedAtEpochMilli)
        );
    }

    public ExperimentExecution failed(ExecutionExceptionSnapshot exception) {
        return new ExperimentExecution(
                executionId,
                handler,
                request,
                response,
                logs,
                exception,
                startedAtEpochMilli,
                completedAtEpochMilli,
                durationMillis
        );
    }
}
