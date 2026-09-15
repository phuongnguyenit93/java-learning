package com.example.projectbuild.executioncontext.store;

import com.example.projectbuild.executioncontext.model.ExperimentExecution;

import java.util.List;
import java.util.Optional;
import java.util.function.UnaryOperator;

public interface ExecutionStore {

    void put(ExperimentExecution execution);

    Optional<ExperimentExecution> findById(String executionId);

    Optional<ExperimentExecution> findLatest();

    List<ExperimentExecution> findRecent(int limit);

    void update(String executionId, UnaryOperator<ExperimentExecution> updater);

    void remove(String executionId);
}
