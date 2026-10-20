package com.example.learning.oop.refactoring.model;

import java.util.List;
import java.util.Objects;

/** The deliberately monolithic baseline exposes the same financial contract. */
public final class LegacyCheckout {
    public Pricing price(List<OrderLine> lines, boolean vip) {
        Objects.requireNonNull(lines, "lines");
        if (lines.isEmpty() || lines.size() > 100) {
            throw new IllegalArgumentException("Order requires 1..100 lines");
        }
        long subtotal = 0;
        for (OrderLine line : lines) {
            Objects.requireNonNull(line, "line");
            subtotal = Math.addExact(subtotal, Math.multiplyExact(line.unitPriceVnd(), line.quantity()));
        }
        long discount = vip && subtotal >= 500_000 ? subtotal / 10 : 0;
        long payable = Math.subtractExact(subtotal, discount);
        long shipping = payable >= 700_000 ? 0 : 30_000;
        long total = Math.addExact(payable, shipping);
        return new Pricing(subtotal, discount, shipping, payable, total);
    }
}
