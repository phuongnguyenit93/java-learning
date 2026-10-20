package com.example.learning.oop.interface_chapter.dto;

import java.util.List;

/** JSON view contracts only: all decisions live in Service and model interfaces. */
public final class InterfaceExperimentDtos {
    private InterfaceExperimentDtos() {}

    public record ShippingRequest(Integer distanceKm, Long orderValueVnd, String selectedPolicy) {}
    public record ShippingPrice(String policy, long feeVnd) {}
    public record ShippingResult(ShippingPrice selected, List<ShippingPrice> allPolicies,
                                 boolean freeEconomyShipping) {}

    public record DocumentInput(String id, String title, String content) {}
    public record DocumentAction(String type, String id, String title, String content) {}
    public record DocumentRequest(List<DocumentInput> initialDocuments, List<DocumentAction> actions) {}
    public record DocumentState(String id, String title, String content, String status) {}
    public record DocumentStep(int index, String type, String id, boolean accepted, String detail,
                               List<DocumentState> before, List<DocumentState> after,
                               DocumentState readResult) {}
    public record DocumentResult(List<DocumentState> initial, List<DocumentStep> steps,
                                 List<DocumentState> current, boolean sharedInstance,
                                 String defaultConflictResolution, String scope) {}

    public record PaymentInput(String requestId, String orderId, Long amountVnd) {}
    public record CheckoutRequest(String gateway, String repository, List<PaymentInput> actions) {}
    public record ReceiptState(String requestId, String orderId, long amountVnd, String status) {}
    public record CheckoutState(List<ReceiptState> storedPayments, int gatewayCalls) {}
    public record CheckoutStep(int index, String requestId, boolean accepted, boolean replayed,
                               String detail, ReceiptState receipt,
                               CheckoutState before, CheckoutState after) {}
    public record CheckoutResult(String gateway, String repository,
                                 CheckoutState initial, List<CheckoutStep> steps,
                                 CheckoutState current, String scope) {}

    public record ErrorResponse(String error) {}
}
