package com.example.learning.oop.interface_chapter.service;

import com.example.learning.oop.interface_chapter.model.ShippingFeePolicy;
import java.util.Objects;

public final class ShippingQuoteService {
    private final ShippingFeePolicy policy;

    public ShippingQuoteService(ShippingFeePolicy policy) {
        this.policy = Objects.requireNonNull(policy, "shipping policy");
    }

    public long quote(int distanceKm, long orderValueVnd) {
        return policy.quote(distanceKm, orderValueVnd);
    }

    public String policyCode() { return policy.code(); }
}
