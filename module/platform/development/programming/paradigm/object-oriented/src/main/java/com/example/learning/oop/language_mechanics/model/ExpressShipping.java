package com.example.learning.oop.language_mechanics.model;

public final class ExpressShipping implements ShippingPolicy {
    @Override
    public long quoteVnd(int weightGrams) {
        long billedKilograms = (weightGrams + 999L) / 1000L;
        return Math.addExact(35_000L, Math.multiplyExact(8_000L, billedKilograms));
    }
}
