package com.example.learning.oop.composition.model;

import com.example.learning.oop.composition.dto.CompositionDtos.PolicyKind;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * AtomicReference safely publishes replacement immutable collaborators.
 * Each quote reads its collaborator once; this is not a resource-draining protocol.
 */
public final class SwitchablePricing {
    public interface Policy {
        String name();
        long payable(long subtotal);
    }

    public record NoDiscount() implements Policy {
        @Override public String name() { return "NONE"; }
        @Override public long payable(long subtotal) { return checkedSubtotal(subtotal); }
    }

    public record FixedDiscount(long discountCents) implements Policy {
        public FixedDiscount {
            if (discountCents < 0 || discountCents > 100_000_000) {
                throw new IllegalArgumentException("discountCents must be 0-100000000");
            }
        }
        @Override public String name() { return "FIXED"; }
        @Override public long payable(long subtotal) {
            return checkedSubtotal(subtotal) - Math.min(discountCents, subtotal);
        }
    }

    public record PercentDiscount(int percent) implements Policy {
        public PercentDiscount {
            if (percent < 0 || percent > 100) {
                throw new IllegalArgumentException("percent must be 0-100");
            }
        }
        @Override public String name() { return "PERCENT"; }
        @Override public long payable(long subtotal) {
            checkedSubtotal(subtotal);
            return subtotal - subtotal * percent / 100L;
        }
    }

    public record Quote(String usedPolicy, long payableCents) {
    }

    private final AtomicReference<Policy> current;

    public SwitchablePricing(Policy initial) {
        current = new AtomicReference<>(Objects.requireNonNull(initial));
    }

    public String activeName() {
        return current.get().name();
    }

    public void replace(Policy next) {
        current.set(Objects.requireNonNull(next));
    }

    public Quote quote(long subtotalCents) {
        // The same immutable collaborator is used for name and calculation.
        Policy snapshot = current.get();
        return quoteWith(subtotalCents, snapshot);
    }

    /** Method-parameter injection: does not change the active shared collaborator. */
    public Quote quoteWith(long subtotalCents, Policy perCall) {
        Policy selected = Objects.requireNonNull(perCall);
        long net = selected.payable(subtotalCents);
        if (net < 0 || net > subtotalCents) {
            throw new IllegalStateException("Policy violates payable range contract");
        }
        return new Quote(selected.name(), net);
    }

    public static Policy create(PolicyKind kind, Integer percent, Long fixedCents) {
        if (kind == null) throw new IllegalArgumentException("policy kind is required");
        return switch (kind) {
            case NONE -> new NoDiscount();
            case FIXED -> new FixedDiscount(require(fixedCents, "discountCents"));
            case PERCENT -> new PercentDiscount(require(percent, "percent"));
        };
    }

    private static int require(Integer v, String name) {
        if (v == null) throw new IllegalArgumentException(name + " is required");
        return v;
    }

    private static long require(Long v, String name) {
        if (v == null) throw new IllegalArgumentException(name + " is required");
        return v;
    }

    private static long checkedSubtotal(long value) {
        if (value < 0 || value > 100_000_000L) {
            throw new IllegalArgumentException("subtotalCents must be 0-100000000");
        }
        return value;
    }
}
