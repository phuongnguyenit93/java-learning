package com.example.learning.oop.solid.model;

import org.springframework.stereotype.Component;

@Component
public final class NoDiscountPolicy implements DiscountPolicy {
    @Override public String code() { return "NONE"; }

    @Override public long discountCents(long subtotalCents) {
        return 0;
    }
}
