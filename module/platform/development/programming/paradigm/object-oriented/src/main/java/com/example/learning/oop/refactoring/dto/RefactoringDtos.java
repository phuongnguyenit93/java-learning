package com.example.learning.oop.refactoring.dto;

import java.util.List;

public final class RefactoringDtos {
    private RefactoringDtos() {}

    public record LineInput(String sku, long unitPriceVnd, int quantity) {}
    public record CheckoutRequest(boolean vip, List<LineInput> lines) {}
    public record PricingView(long subtotalVnd, long discountVnd, long shippingVnd,
                              long payableVnd, long totalVnd, String receipt) {}
    public record CheckoutComparison(PricingView before, PricingView after, boolean equivalent) {}

    public record DeliveryInput(String type, long orderValueVnd, int weightKg) {}
    public record DeliveryRequest(List<DeliveryInput> deliveries) {}
    public record DeliveryView(long feeVnd, int etaDays) {}
    public record DeliveryComparison(String type, DeliveryView before, DeliveryView after,
                                     boolean equivalent) {}
    public record DeliveryResult(List<DeliveryComparison> deliveries, boolean allEquivalent) {}

    public record AlertRequest(String channel, String recipient, String incident,
                               int transientFailures, int maxAttempts) {}
    public record AlertResult(boolean delivered, int attempts, List<String> attemptEvents,
                              List<String> deliveredMessages, String outcome) {}
}
