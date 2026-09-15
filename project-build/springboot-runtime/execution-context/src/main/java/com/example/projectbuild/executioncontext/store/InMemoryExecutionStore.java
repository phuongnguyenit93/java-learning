package com.example.projectbuild.executioncontext.store;

import com.example.projectbuild.executioncontext.model.ExperimentExecution;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.function.UnaryOperator;

public class InMemoryExecutionStore implements ExecutionStore {

    private final int maxHistory;
    private final ConcurrentHashMap<String, ExperimentExecution> executions = new ConcurrentHashMap<>();
    private final ConcurrentLinkedDeque<String> order = new ConcurrentLinkedDeque<>();

    public InMemoryExecutionStore(int maxHistory) {
        if (maxHistory < 1) {
            throw new IllegalArgumentException("maxHistory must be greater than zero");
        }
        this.maxHistory = maxHistory;
    }

    @Override
    public void put(ExperimentExecution execution) {
        executions.put(execution.executionId(), execution);
        order.remove(execution.executionId());
        order.addFirst(execution.executionId());
        trimHistory();
    }

    @Override
    public Optional<ExperimentExecution> findById(String executionId) {
        return Optional.ofNullable(executions.get(executionId));
    }

    @Override
    public Optional<ExperimentExecution> findLatest() {
        String latestId = order.peekFirst();
        return latestId == null ? Optional.empty() : findById(latestId);
    }

    @Override
    public List<ExperimentExecution> findRecent(int limit) {
        if (limit <= 0) {
            return List.of();
        }

        List<ExperimentExecution> result = new ArrayList<>();
        for (String executionId : order) {
            ExperimentExecution execution = executions.get(executionId);
            if (execution != null) {
                result.add(execution);
            }
            if (result.size() >= limit) {
                break;
            }
        }
        return List.copyOf(result);
    }

    @Override
    public void update(String executionId, UnaryOperator<ExperimentExecution> updater) {
        executions.computeIfPresent(executionId, (ignored, current) -> updater.apply(current));
    }

    @Override
    public void remove(String executionId) {
        executions.remove(executionId);
        order.remove(executionId);
    }

    private void trimHistory() {
        while (order.size() > maxHistory) {
            String removedId = order.pollLast();
            if (removedId != null) {
                executions.remove(removedId);
            }
        }
    }
}
