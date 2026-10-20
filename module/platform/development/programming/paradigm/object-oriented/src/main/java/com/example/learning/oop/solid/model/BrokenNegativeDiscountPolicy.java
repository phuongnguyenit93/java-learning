package com.example.learning.oop.solid.model;

import org.springframework.stereotype.Component;

/** Deliberately violates LSP so the experiment can observe a rejected policy. */
@Component
public final class BrokenNegativeDiscountPolicy implements DiscountPolicy {
    @Override public String code() { return "BROKEN_NEGATIVE"; }

    @Override public long discountCents(long subtotalCents) {
        return -1;
    }
}
