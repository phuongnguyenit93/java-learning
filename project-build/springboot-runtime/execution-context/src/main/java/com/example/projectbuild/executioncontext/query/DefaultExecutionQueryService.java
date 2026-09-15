package com.example.projectbuild.executioncontext.query;

import com.example.projectbuild.executioncontext.model.ExecutionHandlerSnapshot;
import com.example.projectbuild.executioncontext.model.ExecutionRequestSnapshot;
import com.example.projectbuild.executioncontext.model.ExecutionResponseSnapshot;
import com.example.projectbuild.executioncontext.model.ExecutionSummary;
import com.example.projectbuild.executioncontext.model.ExperimentContext;
import com.example.projectbuild.executioncontext.model.ExperimentExecution;
import com.example.projectbuild.executioncontext.service.ExecutionContextService;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public class DefaultExecutionQueryService implements ExecutionQueryService {

    private static final int DEFAULT_LIMIT = 20;

    private final ExecutionContextService executionContextService;

    public DefaultExecutionQueryService(ExecutionContextService executionContextService) {
        this.executionContextService = executionContextService;
    }

    @Override
    public Optional<ExperimentContext> findLatest() {
        return executionContextService.findLatest()
                .flatMap(execution -> executionContextService.getContext(execution.executionId()));
    }

    @Override
    public Optional<ExperimentContext> findById(String executionId) {
        if (executionId == null || executionId.isBlank()) {
            return Optional.empty();
        }
        return executionContextService.getContext(executionId);
    }

    @Override
    public List<ExecutionSummary> find(ExecutionQuery query) {
        ExecutionQuery effectiveQuery = query == null ? ExecutionQuery.recent(DEFAULT_LIMIT) : query;
        int limit = effectiveLimit(effectiveQuery.limit());

        return executionContextService.findRecent(Integer.MAX_VALUE)
                .stream()
                .filter(execution -> matches(execution, effectiveQuery))
                .limit(limit)
                .map(DefaultExecutionQueryService::toSummary)
                .toList();
    }

    @Override
    public List<ExperimentContext> findContexts(ExecutionQuery query) {
        return find(query)
                .stream()
                .map(ExecutionSummary::executionId)
                .map(this::findById)
                .flatMap(Optional::stream)
                .toList();
    }

    @Override
    public List<ExperimentContext> findContexts(List<String> executionIds) {
        if (executionIds == null || executionIds.isEmpty()) {
            return List.of();
        }

        List<ExperimentContext> result = new ArrayList<>();
        for (String executionId : executionIds) {
            findById(executionId).ifPresent(result::add);
        }
        return List.copyOf(result);
    }

    private static int effectiveLimit(Integer requestedLimit) {
        if (requestedLimit == null) {
            return DEFAULT_LIMIT;
        }
        if (requestedLimit <= 0) {
            return 0;
        }
        return requestedLimit;
    }

    private static boolean matches(
            ExperimentExecution execution,
            ExecutionQuery query
    ) {
        if (query.fromEpochMilli() != null && execution.startedAtEpochMilli() < query.fromEpochMilli()) {
            return false;
        }
        if (query.toEpochMilli() != null && execution.startedAtEpochMilli() > query.toEpochMilli()) {
            return false;
        }

        ExecutionRequestSnapshot request = execution.request();
        ExecutionResponseSnapshot response = execution.response();
        ExecutionHandlerSnapshot handler = execution.handler();

        if (!equalsIgnoreCase(query.httpMethod(), request == null ? null : request.method())) {
            return false;
        }
        if (!equalsExact(query.path(), request == null ? null : request.path())) {
            return false;
        }
        if (!containsIgnoreCase(query.controller(), handler == null ? null : handler.controllerClass())) {
            return false;
        }
        if (!containsIgnoreCase(query.method(), handler == null ? null : handler.method())) {
            return false;
        }
        if (query.status() != null && (response == null || response.status() != query.status())) {
            return false;
        }

        boolean failed = isFailed(execution);
        if (query.failed() != null && failed != query.failed()) {
            return false;
        }
        if (query.minDurationMillis() != null && execution.durationMillis() < query.minDurationMillis()) {
            return false;
        }
        if (query.maxDurationMillis() != null && execution.durationMillis() > query.maxDurationMillis()) {
            return false;
        }

        return matchesSearch(execution, query.search());
    }

    private static boolean matchesSearch(
            ExperimentExecution execution,
            String search
    ) {
        if (search == null || search.isBlank()) {
            return true;
        }

        String expected = search.trim().toLowerCase(Locale.ROOT);
        ExecutionRequestSnapshot request = execution.request();
        ExecutionHandlerSnapshot handler = execution.handler();

        return contains(execution.executionId(), expected)
                || contains(request == null ? null : request.method(), expected)
                || contains(request == null ? null : request.path(), expected)
                || contains(handler == null ? null : handler.controllerClass(), expected)
                || contains(handler == null ? null : handler.method(), expected)
                || contains(handler == null ? null : handler.signature(), expected);
    }

    private static ExecutionSummary toSummary(ExperimentExecution execution) {
        ExecutionRequestSnapshot request = execution.request();
        ExecutionResponseSnapshot response = execution.response();
        ExecutionHandlerSnapshot handler = execution.handler();

        return new ExecutionSummary(
                execution.executionId(),
                execution.startedAtEpochMilli(),
                execution.completedAtEpochMilli(),
                request == null ? "" : nullToEmpty(request.method()),
                request == null ? "" : nullToEmpty(request.path()),
                handler == null ? "" : nullToEmpty(handler.controllerClass()),
                handler == null ? "" : nullToEmpty(handler.method()),
                handler == null ? "" : nullToEmpty(handler.signature()),
                response == null ? null : response.status(),
                execution.durationMillis(),
                isFailed(execution),
                execution.logs().size()
        );
    }

    private static boolean isFailed(ExperimentExecution execution) {
        return execution.exception() != null
                || (execution.response() != null && execution.response().status() >= 400);
    }

    private static boolean equalsIgnoreCase(String expected, String actual) {
        return expected == null || expected.isBlank()
                || (actual != null && expected.trim().equalsIgnoreCase(actual));
    }

    private static boolean equalsExact(String expected, String actual) {
        return expected == null || expected.isBlank()
                || (actual != null && expected.trim().equals(actual));
    }

    private static boolean containsIgnoreCase(String expected, String actual) {
        return expected == null || expected.isBlank()
                || (actual != null && actual.toLowerCase(Locale.ROOT)
                .contains(expected.trim().toLowerCase(Locale.ROOT)));
    }

    private static boolean contains(String actual, String expectedLowerCase) {
        return actual != null && actual.toLowerCase(Locale.ROOT).contains(expectedLowerCase);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
