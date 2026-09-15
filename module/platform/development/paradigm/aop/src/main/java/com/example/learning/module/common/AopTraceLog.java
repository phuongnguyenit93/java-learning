package com.example.learning.module.common;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class AopTraceLog {

    private final ThreadLocal<List<String>> events =
            ThreadLocal.withInitial(ArrayList::new);

    public void reset() {
        events.get().clear();
    }

    public void add(String event) {
        events.get().add(event);
    }

    public List<String> snapshot() {
        return List.copyOf(events.get());
    }

    public List<String> snapshotAndClear() {
        List<String> snapshot = List.copyOf(events.get());
        events.remove();
        return snapshot;
    }
}
