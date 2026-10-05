package com.example.learning.module.messaging.stomp;

import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicReference;

@Component
public class StompExperimentProbe {

    private final AtomicReference<Observation> observation = new AtomicReference<>();

    public void reset() {
        observation.set(null);
    }

    public void record(String payload) {
        observation.set(new Observation(payload, Thread.currentThread().getName()));
    }

    public Observation snapshot() {
        return observation.get();
    }

    public record Observation(String payload, String handlerThread) {
    }
}
