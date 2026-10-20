package com.example.learning.oop.interface_chapter.model;

public final class EconomyShippingPolicy extends DistanceShippingPolicy {
    public EconomyShippingPolicy() { super("ECONOMY", 10_000L, 2_500L); }

    @Override protected boolean isFree(long orderValueVnd) {
        return orderValueVnd >= 500_000L;
    }
}
