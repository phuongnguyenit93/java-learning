package com.example.learning.oop.language_mechanics.model;

public final class StandardShipping implements ShippingPolicy {
    @Override
    public long quoteVnd(int weightGrams) {
        return 20_000L;
    }
}
