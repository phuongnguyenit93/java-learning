package com.example.learning.oop.refactoring.model;

/** Extracted pricing responsibility; no transport, persistence, or receipt formatting. */
public final class PricingPolicy {
    public Pricing calculate(ShoppingOrder order) {
        long subtotal = order.subtotalVnd();
        long discount = calculateDiscount(subtotal, order.isVip());
        long payable = Math.subtractExact(subtotal, discount);
        long shipping = calculateShipping(payable);
        return new Pricing(subtotal, discount, shipping, payable,
                Math.addExact(payable, shipping));
    }

    private long calculateDiscount(long subtotal, boolean vip) {
        return vip && subtotal >= 500_000 ? subtotal / 10 : 0;
    }

    private long calculateShipping(long payable) {
        return payable >= 700_000 ? 0 : 30_000;
    }
}
