package com.example.learning.casestudy.domain.group8.case13;

import com.example.learning.casestudy.port.group8.case13.DiscountPolicy;

public final class NoDiscount implements DiscountPolicy {
    public long discount(long cents) { return 0; }
}
