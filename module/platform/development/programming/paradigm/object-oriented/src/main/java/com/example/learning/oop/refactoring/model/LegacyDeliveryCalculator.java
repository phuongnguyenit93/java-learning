package com.example.learning.oop.refactoring.model;

/** Baseline using type-conditional branches; retained only for differential verification. */
public final class LegacyDeliveryCalculator {
    public DeliveryQuote quote(DeliveryType type, long orderValueVnd, int weightKg) {
        return switch (type) {
            case STANDARD -> new DeliveryQuote(orderValueVnd >= 500_000 ? 0L :
                    Math.addExact(20_000L, Math.multiplyExact(5_000L, weightKg)), 5);
            case EXPRESS -> new DeliveryQuote(
                    Math.addExact(45_000L, Math.multiplyExact(10_000L, weightKg)), 1);
            case PICKUP -> new DeliveryQuote(0L, 0);
        };
    }
}
