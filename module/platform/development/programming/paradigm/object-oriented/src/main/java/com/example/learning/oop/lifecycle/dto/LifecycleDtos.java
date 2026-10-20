package com.example.learning.oop.lifecycle.dto;

import java.util.List;
import java.util.Map;

/** JSON contracts for bounded, per-request lifecycle experiments. */
public final class LifecycleDtos {
    private LifecycleDtos() {
    }

    public enum CreationStyle {
        CONSTRUCTOR, STATIC_FACTORY, BUILDER
    }

    public record CreationRequest(String courseCode, Integer capacity,
                                  List<String> students, CreationStyle style) {
    }

    public record CreationResult(CreationStyle style, String courseCode,
                                 int capacity, List<String> students, int availableSeats) {
    }

    public record NodeInput(String id, List<String> links) {
    }

    public enum RootAction {
        RETAIN, RELEASE
    }

    public record RootStep(RootAction action, String nodeId) {
    }

    public record ReferenceRequest(List<NodeInput> nodes, List<String> initialRoots,
                                   List<RootStep> steps) {
    }

    public record ReferenceSnapshot(int step, List<String> roots,
                                    List<String> reachableFromRoots,
                                    List<String> notReachableFromRoots) {
    }

    public record ReferenceResult(int nodeCount, List<ReferenceSnapshot> snapshots,
                                  String interpretation) {
    }

    public record ResourceRequest(boolean bodyFails, boolean outerCloseFails,
                                  boolean innerCloseFails) {
    }

    public record ResourceResult(List<String> trace, String primaryFailure,
                                 List<String> suppressedFailures,
                                 Map<String, Boolean> closed, boolean completedWithoutFailure) {
    }

    public record PublicationRequest(String label) {
    }

    public record PublicationResult(String unsafeObservedOnRegister,
                                    String unsafeObservedAfterConstruction,
                                    String safeObservedOnRegister,
                                    String safeObservedAfterConstruction,
                                    int unsafeSubscriptions, int safeSubscriptions) {
    }

    public record ErrorResult(String code, String message) {
    }
}
