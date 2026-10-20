package com.example.learning.oop.composition.dto;

import java.util.List;

/** Bounded, request-local contracts for observable object-composition experiments. */
public final class CompositionDtos {
    private CompositionDtos() {
    }

    public enum Format {
        FULL, COMPACT
    }

    public enum Channel {
        EMAIL, SMS
    }

    public record NotificationRequest(String orderId, String recipient, long totalCents,
                                      Format format, Channel channel, boolean audited,
                                      boolean failTransport) {
    }

    public record NotificationResponse(String channel, String content, boolean accepted,
                                       String failure, List<String> trace) {
    }

    public enum PolicyKind {
        NONE, FIXED, PERCENT
    }

    public enum PolicyAction {
        SWITCH, QUOTE, QUOTE_WITH_POLICY
    }

    public record PolicyStep(PolicyAction action, PolicyKind policy,
                             Integer percent, Long discountCents, Long subtotalCents) {
    }

    public record PolicyRequest(PolicyKind initialPolicy, Integer initialPercent,
                                Long initialDiscountCents, List<PolicyStep> steps) {
    }

    public record PolicyStepResult(int step, PolicyAction action, String activePolicy,
                                   String usedPolicy, Long subtotalCents, Long payableCents) {
    }

    public record PolicyResponse(String initialPolicy, String finalPolicy,
                                 List<PolicyStepResult> timeline) {
    }

    public record OrderLineInput(String sku, int quantity, long unitPriceCents) {
    }

    public record PlaceOrderRequest(String orderId, List<OrderLineInput> lines, boolean failStorage) {
    }

    public record PlacedOrder(String orderId, long totalCents, List<OrderLineInput> lines) {
    }

    public record PlaceOrderResponse(boolean accepted, String failure, int saveAttempts,
                                     List<PlacedOrder> storedOrders, String storageDurability) {
    }

    public enum Ownership {
        OWNED, BORROWED
    }

    public record ResourceRequest(Ownership ownership, String content, boolean failWrite) {
    }

    public record ResourceResponse(Ownership ownership, boolean exporterClosed,
                                   boolean resourceClosedAfterExporter,
                                   boolean resourceClosedAfterCaller,
                                   String failure, List<String> trace) {
    }

    public record ErrorResult(String code, String message) {
    }
}
