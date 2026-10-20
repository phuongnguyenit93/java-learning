package com.example.learning.oop.refactoring.model;

public final class PickupDeliveryPolicy implements DeliveryPolicy {
    @Override
    public DeliveryQuote quote(long orderValueVnd, int weightKg) {
        return new DeliveryQuote(0L, 0);
    }
}
