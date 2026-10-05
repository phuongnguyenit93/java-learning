package com.example.learning.transaction.support;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class ExperimentTraceRecorder {

    private final ConcurrentHashMap<String, CopyOnWriteArrayList<String>> traces =
            new ConcurrentHashMap<>();

    public void record(String scenarioId, String event) {
        traces.computeIfAbsent(
                scenarioId,
                ignored -> new CopyOnWriteArrayList<>()
        ).add(event);
    }

    public List<String> snapshot(String scenarioId) {
        List<String> trace = traces.get(scenarioId);
        return trace == null ? List.of() : new ArrayList<>(trace);
    }

    public void clear(String scenarioId) {
        traces.remove(scenarioId);
    }
}
