package com.example.learning.oop.inheritance.model;

import java.util.ArrayList;
import java.util.List;

/** Observe constructor order and the risk of calling an override before child initialization. */
public final class ConstructorProbe {
    private ConstructorProbe() {
    }

    public record Trace(
            List<String> unsafe, List<String> safe, String valueDuringParent,
            String valueAfterConstruction) {
        public Trace {
            unsafe = List.copyOf(unsafe);
            safe = List.copyOf(safe);
        }
    }

    public static Trace run(String label) {
        if (label == null || label.isBlank() || label.length() > 80) {
            throw new IllegalArgumentException("initialLabel must contain 1..80 nonblank characters");
        }
        List<String> unsafe = new ArrayList<>();
        UnsafeChild early = new UnsafeChild(unsafe, label.trim());
        List<String> safe = new ArrayList<>();
        SafeChild ready = new SafeChild(safe, label.trim());
        ready.inspectAfterInitialization();
        return new Trace(unsafe, safe, early.observedDuringParent, early.value());
    }

    private static class UnsafeParent {
        UnsafeParent(List<String> steps) {
            steps.add("parent constructor begins");
            // Virtual dispatch reaches UnsafeChild before its field initializer runs.
            steps.add("parent sees overridden value: " + value());
        }

        String value() {
            return "parent";
        }
    }

    private static final class UnsafeChild extends UnsafeParent {
        private String label = "READY";
        private final String observedDuringParent;

        UnsafeChild(List<String> steps, String requestedLabel) {
            super(steps);
            // The super call already dispatched while the field was still null.
            observedDuringParent = steps.get(1).substring("parent sees overridden value: ".length());
            label = requestedLabel;
            steps.add("child constructor completed: " + label);
        }

        @Override
        String value() {
            return label;
        }
    }

    private static class SafeParent {
        SafeParent(List<String> steps) {
            steps.add("parent constructor: no overridable calls");
        }
    }

    private static final class SafeChild extends SafeParent {
        private final String label;
        private final List<String> steps;

        SafeChild(List<String> steps, String requestedLabel) {
            super(steps);
            this.steps = steps;
            this.label = requestedLabel;
            steps.add("child constructor completed: " + label);
        }

        void inspectAfterInitialization() {
            steps.add("post-construction value: " + label);
        }
    }
}
