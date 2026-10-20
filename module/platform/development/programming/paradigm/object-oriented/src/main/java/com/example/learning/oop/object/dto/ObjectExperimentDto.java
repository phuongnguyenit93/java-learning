package com.example.learning.oop.object.dto;

import java.math.BigDecimal;
import java.util.List;

/** JSON contracts for bounded, per-request OOP experiments. */
public final class ObjectExperimentDto {
    private ObjectExperimentDto() {
    }

    public record ReferenceRequest(String name, Integer initialValue, Integer increment) {
    }

    public record ReferenceResult(
            boolean aliasIsSameInstance,
            boolean separatelyCreatedIsDifferentInstance,
            boolean separatelyCreatedInitiallyHadSameState,
            boolean callerReferenceSurvivesParameterReassignment,
            int before,
            int afterThroughOriginal,
            int afterThroughAlias,
            int independentInstanceValue,
            String name) {
    }

    public record EqualityRequest(String firstCode, String secondCode) {
    }

    public record EqualityResult(
            boolean sameInstance,
            boolean valueEquals,
            boolean hashCodesEqual,
            int hashSetSize,
            boolean defaultObjectEquals,
            boolean aliasIsSameInstance) {
    }

    public record CopyRequest(String originalLabel, String updatedLabel, String addedLabel) {
    }

    public record CopyResult(
            boolean outerListsAreDifferent,
            boolean shallowCopySharesElement,
            boolean deepCopyHasIndependentElement,
            List<String> originalAfterMutation,
            List<String> shallowAfterMutation,
            List<String> deepAfterMutation,
            List<String> unmodifiableSnapshotAfterMutation,
            String immutableValueSnapshot) {
    }

    public record AbsenceRequest(String lookupId, String optionalAddress) {
    }

    public record AbsenceResult(
            String lookupId,
            boolean found,
            boolean optionalAddressPresent,
            String customerName,
            List<String> addresses,
            boolean emptyCollectionMeansNoItems) {
    }

    public record CheckoutRequest(
            String sku,
            Integer startingStock,
            Integer quantity,
            BigDecimal unitPrice,
            Boolean paymentApproved) {
    }

    public record WorkflowEvent(int step, String actor, String operation, String observation, int stockAfter) {
    }

    public record CheckoutResult(
            String outcome,
            boolean simulatedOnly,
            String orderId,
            String transactionId,
            BigDecimal total,
            int stockBefore,
            int stockAfter,
            boolean notificationSent,
            List<WorkflowEvent> events) {
    }

    public record ApiError(String error) {
    }
}
