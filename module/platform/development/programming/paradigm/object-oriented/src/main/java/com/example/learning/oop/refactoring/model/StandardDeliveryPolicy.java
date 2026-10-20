package com.example.learning.oop.refactoring.model;

public final class StandardDeliveryPolicy implements DeliveryPolicy {
    @Override
    public DeliveryQuote quote(long orderValueVnd, int weightKg) {
        return new DeliveryQuote(orderValueVnd >= 500_000 ? 0L :
                Math.addExact(20_000L, Math.multiplyExact(5_000L, weightKg)), 5);
    }
}
