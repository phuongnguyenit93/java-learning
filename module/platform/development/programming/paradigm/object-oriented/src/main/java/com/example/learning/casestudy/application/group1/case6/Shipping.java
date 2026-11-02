package com.example.learning.casestudy.application.group1.case6;

import com.example.learning.casestudy.port.group1.case6.ShippingPolicy;
import java.util.Map;

public final class Shipping {
    private final Map<String,ShippingPolicy> policies;
    public Shipping(Map<String,ShippingPolicy> policies) {
        this.policies = Map.copyOf(policies);
    }
    public String dispatch(String type, long grams) {
        ShippingPolicy p = policies.get(type);
        if (p == null || !p.accepts(grams))
            throw new IllegalArgumentException("Unsupported shipment");
        return p.label() + ":" + p.fee(grams);
    }
}
