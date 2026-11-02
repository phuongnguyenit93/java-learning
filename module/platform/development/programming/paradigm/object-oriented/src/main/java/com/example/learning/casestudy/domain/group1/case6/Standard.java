package com.example.learning.casestudy.domain.group1.case6;

import com.example.learning.casestudy.port.group1.case6.ShippingPolicy;

public record Standard() implements ShippingPolicy {
    public boolean accepts(long grams) { return grams > 0 && grams <= 20_000; }
    public long fee(long grams) { return 15_000; }
    public String label() { return "STD"; }
}
