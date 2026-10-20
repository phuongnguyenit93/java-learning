package com.example.learning.oop.class_design.model;

import java.util.Currency;
import java.util.Objects;

/** Value identity: equal currency plus equal minor units; rejects cross-currency arithmetic. */
public record Money(long cents, Currency currency) {
    public Money {
        Objects.requireNonNull(currency, "currency");
        if (cents < 0) {
            throw new IllegalArgumentException("Money amount cannot be negative");
        }
    }

    public Money plus(Money other) {
        ensureCurrency(other);
        return new Money(Math.addExact(cents, other.cents), currency);
    }

    public Money minus(Money other) {
        ensureCurrency(other);
        if (other.cents > cents) {
            throw new IllegalStateException("INSUFFICIENT_FUNDS");
        }
        return new Money(cents - other.cents, currency);
    }

    private void ensureCurrency(Money other) {
        if (other == null || !currency.equals(other.currency)) {
            throw new IllegalArgumentException("Currency mismatch");
        }
    }
}
