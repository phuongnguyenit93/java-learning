package com.example.learning.oop.solid.model;

/**
 * Implementations accept every nonnegative subtotal and return a discount in
 * [0, subtotal]. They must not mutate orders or perform external side effects.
 */
public interface DiscountPolicy {
    String code();
    long discountCents(long subtotalCents);
}
