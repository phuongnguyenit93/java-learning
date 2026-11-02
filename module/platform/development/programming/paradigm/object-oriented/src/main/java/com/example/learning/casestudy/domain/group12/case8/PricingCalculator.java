package com.example.learning.casestudy.domain.group12.case8;

import com.example.learning.casestudy.port.group12.case8.DiscountPolicy;

public class PricingCalculator {
    private final DiscountPolicy policy;
    public PricingCalculator(DiscountPolicy policy) {
        this.policy = policy;
    }
    public int total(int subtotalCents) {
        if (subtotalCents < 0) throw new IllegalArgumentException();
        int discount = policy.discountCents(subtotalCents);
        if (discount < 0 || discount > subtotalCents)
        throw new IllegalStateException("Invalid discount");
        return subtotalCents - discount;
    }
}
