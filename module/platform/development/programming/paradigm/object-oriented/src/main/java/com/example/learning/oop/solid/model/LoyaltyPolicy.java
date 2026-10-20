package com.example.learning.oop.solid.model;

import org.springframework.stereotype.Component;

@Component
public final class LoyaltyPolicy implements DiscountPolicy {
    @Override public String code() { return "LOYALTY"; }

    @Override public long discountCents(long subtotalCents) {
        if (subtotalCents < 50_000) {
            return 0;
        }
        // Floor(15% of subtotal) without multiplying the entire long by 15.
        return Math.addExact((subtotalCents / 100) * 15, (subtotalCents % 100) * 15 / 100);
    }
}
