package com.example.learning.oop.refactoring.model;

/** Structural value equality makes comparison independent of implementation. */
public record Pricing(long subtotalVnd, long discountVnd, long shippingVnd, long payableVnd, long totalVnd) {}
