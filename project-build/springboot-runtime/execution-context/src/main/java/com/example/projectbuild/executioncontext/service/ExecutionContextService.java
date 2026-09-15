package com.example.projectbuild.executioncontext.service;

import com.example.projectbuild.executioncontext.model.ExecutionExceptionSnapshot;
import com.example.projectbuild.executioncontext.model.ExecutionHandlerSnapshot;
import com.example.projectbuild.executioncontext.model.ExecutionLogEntry;
import com.example.projectbuild.executioncontext.model.ExecutionRequestSnapshot;
import com.example.projectbuild.executioncontext.model.ExecutionResponseSnapshot;
import com.example.projectbuild.executioncontext.model.ExperimentContext;
import com.example.projectbuild.executioncontext.model.ExperimentExecution;

import java.util.List;
import java.util.Optional;

public interface ExecutionContextService {

    String start(ExecutionRequestSnapshot request);

    void updateRequest(String executionId, ExecutionRequestSnapshot request);

    void bindHandler(String executionId, ExecutionHandlerSnapshot handler);

    boolean hasHandler(String executionId);

    void addLog(String executionId, ExecutionLogEntry logEntry);

    void complete(String executionId, ExecutionResponseSnapshot response);

    void fail(String executionId, ExecutionExceptionSnapshot exception);

    void discard(String executionId);

    Optional<ExperimentExecution> findById(String executionId);

    Optional<ExperimentExecution> findLatest();

    List<ExperimentExecution> findRecent(int limit);

    Optional<ExperimentContext> getContext(String executionId);
}
