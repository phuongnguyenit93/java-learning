package com.example.learning.oop.refactoring.model;

public final class ExpressDeliveryPolicy implements DeliveryPolicy {
    @Override
    public DeliveryQuote quote(long orderValueVnd, int weightKg) {
        return new DeliveryQuote(Math.addExact(45_000L,
                Math.multiplyExact(10_000L, weightKg)), 1);
    }
}
