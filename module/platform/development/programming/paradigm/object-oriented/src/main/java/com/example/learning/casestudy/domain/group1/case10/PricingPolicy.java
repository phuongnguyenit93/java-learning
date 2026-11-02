package com.example.learning.casestudy.domain.group1.case10;

import com.example.learning.casestudy.model.group1.case10.Quote;

public final class PricingPolicy {
    public Quote quote(long unitPrice, int units, long discount) {
        if (unitPrice < 0 || units < 1 || discount < 0)
            throw new IllegalArgumentException("invalid price");
        long subtotal = Math.multiplyExact(unitPrice, units);
        if (discount > subtotal)
            throw new IllegalArgumentException("discount > subtotal");
        return new Quote(subtotal, discount, subtotal - discount);
    }
}
