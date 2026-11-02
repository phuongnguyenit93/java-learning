package com.example.learning.casestudy.application.group8.case13;

import com.example.learning.casestudy.port.group8.case13.DiscountPolicy;

public record CheckoutCalculator(DiscountPolicy policy) {
    public long total(long baseCents) {
        if (baseCents < 0) throw new IllegalArgumentException("amount");
        long discount = policy.discount(baseCents);
        if (discount < 0 || discount > baseCents)
            throw new IllegalStateException("invalid discount policy");
        return baseCents - discount;
    }
}
