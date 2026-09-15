package com.example.projectbuild.executioncontext.service;

import com.example.projectbuild.executioncontext.model.ExecutionExceptionSnapshot;
import com.example.projectbuild.executioncontext.model.ExecutionHandlerSnapshot;
import com.example.projectbuild.executioncontext.model.ExecutionLogEntry;
import com.example.projectbuild.executioncontext.model.ExecutionRequestSnapshot;
import com.example.projectbuild.executioncontext.model.ExecutionResponseSnapshot;
import com.example.projectbuild.executioncontext.model.ExperimentContext;
import com.example.projectbuild.executioncontext.model.ExperimentExecution;
import com.example.projectbuild.executioncontext.source.ExecutionSourceContextRepository;
import com.example.projectbuild.executioncontext.store.ExecutionStore;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class DefaultExecutionContextService implements ExecutionContextService {

    private final ExecutionStore store;
    private final ExecutionSourceContextRepository sourceContextRepository;

    public DefaultExecutionContextService(
            ExecutionStore store,
            ExecutionSourceContextRepository sourceContextRepository
    ) {
        this.store = store;
        this.sourceContextRepository = sourceContextRepository;
    }

    @Override
    public String start(ExecutionRequestSnapshot request) {
        String executionId = UUID.randomUUID().toString();
        store.put(ExperimentExecution.started(executionId, request, System.currentTimeMillis()));
        return executionId;
    }

    @Override
    public void updateRequest(String executionId, ExecutionRequestSnapshot request) {
        store.update(executionId, execution -> execution.withRequest(request));
    }

    @Override
    public void bindHandler(String executionId, ExecutionHandlerSnapshot handler) {
        store.update(executionId, execution -> execution.withHandler(handler));
    }

    @Override
    public boolean hasHandler(String executionId) {
        return store.findById(executionId)
                .map(ExperimentExecution::handler)
                .isPresent();
    }

    @Override
    public void addLog(String executionId, ExecutionLogEntry logEntry) {
        store.update(executionId, execution -> execution.withLog(logEntry));
    }

    @Override
    public void complete(String executionId, ExecutionResponseSnapshot response) {
        long completedAt = System.currentTimeMillis();
        store.update(executionId, execution -> execution.completed(response, completedAt));
    }

    @Override
    public void fail(String executionId, ExecutionExceptionSnapshot exception) {
        store.update(executionId, execution -> execution.failed(exception));
    }

    @Override
    public void discard(String executionId) {
        store.remove(executionId);
    }

    @Override
    public Optional<ExperimentExecution> findById(String executionId) {
        return store.findById(executionId);
    }

    @Override
    public Optional<ExperimentExecution> findLatest() {
        return store.findLatest();
    }

    @Override
    public List<ExperimentExecution> findRecent(int limit) {
        return store.findRecent(limit);
    }

    @Override
    public Optional<ExperimentContext> getContext(String executionId) {
        return store.findById(executionId)
                .map(execution -> new ExperimentContext(
                        execution,
                        execution.handler() == null
                                ? null
                                : sourceContextRepository.find(
                                        execution.handler().controllerClass(),
                                        execution.handler().signature()
                                ).orElse(null)
                ));
    }
}
