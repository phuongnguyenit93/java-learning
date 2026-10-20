package com.example.learning.oop.lifecycle.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Synchronous callback registration exposes a constructor this-escape reliably;
 * no race/sleep/timing assumption or process-global subscriber registry is involved.
 */
public final class PublicationScenario {
    private PublicationScenario() {
    }

    private interface Listener {
        void onEvent();
    }

    private static final class EventRegistry {
        private final List<Listener> listeners = new ArrayList<>();

        void register(Listener listener) {
            listeners.add(listener);
            listener.onEvent(); // immediate callback, including during an unsafe constructor
        }

        int subscriptions() {
            return listeners.size();
        }
    }

    private static final class UnsafeListener implements Listener {
        private final List<String> observed = new ArrayList<>();
        private final String label;

        UnsafeListener(EventRegistry registry, String label) {
            registry.register(this); // bad example, intentionally publishes this
            this.label = label;
        }

        @Override
        public void onEvent() {
            observed.add(label);
        }
    }

    private static final class SafeListener implements Listener {
        private final List<String> observed = new ArrayList<>();
        private final String label;

        private SafeListener(String label) {
            this.label = label;
        }

        static SafeListener createAndRegister(EventRegistry registry, String label) {
            SafeListener ready = new SafeListener(label);
            registry.register(ready);
            return ready;
        }

        @Override
        public void onEvent() {
            observed.add(label);
        }
    }

    public record Observation(String unsafeOnRegister, String unsafeAfterConstruction,
                              String safeOnRegister, String safeAfterConstruction,
                              int unsafeSubscriptions, int safeSubscriptions) {
    }

    public static Observation compare(String label) {
        if (label == null || !label.matches("[A-Za-z0-9_-]{1,40}")) {
            throw new IllegalArgumentException("label must be 1-40 letters, digits, '_' or '-'");
        }
        EventRegistry unsafeRegistry = new EventRegistry();
        UnsafeListener unsafe = new UnsafeListener(unsafeRegistry, label);
        String observedTooEarly = unsafe.observed.get(0); // null: field not assigned yet
        unsafe.onEvent();

        EventRegistry safeRegistry = new EventRegistry();
        SafeListener safe = SafeListener.createAndRegister(safeRegistry, label);
        String observedAfterInit = safe.observed.get(0);
        safe.onEvent();
        return new Observation(observedTooEarly, unsafe.observed.get(1),
                observedAfterInit, safe.observed.get(1),
                unsafeRegistry.subscriptions(), safeRegistry.subscriptions());
    }
}
