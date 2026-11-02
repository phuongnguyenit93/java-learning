package com.example.learning.casestudy.domain.group8.case13;

import com.example.learning.casestudy.port.group8.case13.DiscountPolicy;

public record PercentOff(int percent) implements DiscountPolicy {
    public PercentOff {
        if (percent < 0 || percent > 100)
            throw new IllegalArgumentException("percent");
    }
    public long discount(long cents) {
        return Math.multiplyExact(cents, percent) / 100;
    }
}
