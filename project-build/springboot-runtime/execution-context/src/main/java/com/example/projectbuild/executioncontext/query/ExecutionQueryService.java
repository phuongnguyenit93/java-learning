package com.example.projectbuild.executioncontext.query;

import com.example.projectbuild.executioncontext.model.ExecutionSummary;
import com.example.projectbuild.executioncontext.model.ExperimentContext;

import java.util.List;
import java.util.Optional;

public interface ExecutionQueryService {

    Optional<ExperimentContext> findLatest();

    Optional<ExperimentContext> findById(String executionId);

    List<ExecutionSummary> find(ExecutionQuery query);

    List<ExperimentContext> findContexts(ExecutionQuery query);

    List<ExperimentContext> findContexts(List<String> executionIds);
}
