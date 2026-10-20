package com.example.learning.oop.interface_chapter.model;

public final class ExpressShippingPolicy extends DistanceShippingPolicy {
    public ExpressShippingPolicy() { super("EXPRESS", 20_000L, 5_000L); }
}
