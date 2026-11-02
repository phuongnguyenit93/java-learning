package com.example.learning.casestudy.domain.group1.case6;

import com.example.learning.casestudy.port.group1.case6.ShippingPolicy;

public record Express() implements ShippingPolicy {
    public boolean accepts(long grams) { return grams > 0 && grams <= 5_000; }
    public long fee(long grams) { return 35_000; }
    public String label() { return "EXP"; }
}
