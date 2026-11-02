package com.example.learning.casestudy.domain.group8.case13;

import com.example.learning.casestudy.port.group8.case13.DiscountPolicy;

public record FixedOff(long centsOff) implements DiscountPolicy {
    public FixedOff {
        if (centsOff < 0) throw new IllegalArgumentException("discount");
    }
    public long discount(long cents) { return Math.min(cents, centsOff); }
}
