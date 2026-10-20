package com.example.learning.oop.solid.model;

import org.springframework.stereotype.Component;

@Component
public final class TenPercentPolicy implements DiscountPolicy {
    @Override public String code() { return "TEN_PERCENT"; }

    @Override public long discountCents(long subtotalCents) {
        return subtotalCents / 10;
    }
}
