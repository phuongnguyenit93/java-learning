package com.example.learning.oop.solid.model;

import com.example.learning.oop.solid.exception.PolicyContractException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** The application-level rule depends only on the small policy contract. */
public final class DiscountPricing {
    private final Map<String, DiscountPolicy> policies;

    public DiscountPricing(Collection<? extends DiscountPolicy> implementations) {
        Objects.requireNonNull(implementations, "policies");
        if (implementations.isEmpty()) {
            throw new IllegalArgumentException("at least one policy is required");
        }
        Map<String, DiscountPolicy> selected = new LinkedHashMap<>();
        for (DiscountPolicy policy : implementations) {
            Objects.requireNonNull(policy, "policy");
            if (policy.code() == null || !policy.code().matches("[A-Z_]{2,40}")) {
                throw new IllegalArgumentException("policy code must use 2..40 uppercase letters or underscores");
            }
            if (selected.putIfAbsent(policy.code(), policy) != null) {
                throw new IllegalArgumentException("duplicate policy code: " + policy.code());
            }
        }
        policies = Map.copyOf(selected);
    }

    public Quote quote(String policyCode, long subtotalCents) {
        if (subtotalCents < 0) {
            throw new IllegalArgumentException("subtotalCents must be nonnegative");
        }
        if (policyCode == null || policyCode.isBlank()) {
            throw new IllegalArgumentException("policyCode is required");
        }
        DiscountPolicy policy = policies.get(policyCode);
        if (policy == null) {
            throw new IllegalArgumentException("unknown discount policy: " + policyCode);
        }
        long discount = policy.discountCents(subtotalCents);
        if (discount < 0 || discount > subtotalCents) {
            throw new PolicyContractException("policy " + policyCode + " violated 0 <= discount <= subtotal");
        }
        return new Quote(policyCode, subtotalCents, discount, subtotalCents - discount);
    }

    public record Quote(String policyCode, long subtotalCents, long discountCents, long payableCents) {}
}
