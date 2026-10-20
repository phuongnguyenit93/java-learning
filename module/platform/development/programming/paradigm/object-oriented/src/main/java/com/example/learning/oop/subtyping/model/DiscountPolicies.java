package com.example.learning.oop.subtyping.model;

/**
 * Contract: any price in [0, 100_000_000] must be accepted, returning
 * a deterministic net price within [0, original price] without side effects.
 */
public final class DiscountPolicies {
    private DiscountPolicies() {
    }

    public interface DiscountPolicy {
        long netCents(long priceCents);
    }

    public static long requirePrice(long priceCents) {
        if (priceCents < 0 || priceCents > 100_000_000L) {
            throw new IllegalArgumentException("price must be between 0 and 100000000 cents");
        }
        return priceCents;
    }

    public record FixedDiscount(long discountCents) implements DiscountPolicy {
        public FixedDiscount {
            if (discountCents < 0 || discountCents > 100_000_000L) {
                throw new IllegalArgumentException("discountCents must be 0-100000000");
            }
        }

        @Override
        public long netCents(long priceCents) {
            requirePrice(priceCents);
            return priceCents - Math.min(priceCents, discountCents);
        }
    }

    public record PercentDiscount(int percent) implements DiscountPolicy {
        public PercentDiscount {
            if (percent < 0 || percent > 100) {
                throw new IllegalArgumentException("percent must be between 0 and 100");
            }
        }

        @Override
        public long netCents(long priceCents) {
            requirePrice(priceCents);
            // Bounded price and rate avoid overflow; integer cents round down.
            return priceCents - (priceCents * percent) / 100L;
        }
    }

    /**
     * Intentional LSP counterexample. It compiles as a nominal subtype but
     * rejects prices the common DiscountPolicy contract promises to accept.
     */
    public record StrictMinimumDiscount(long minimumCents) implements DiscountPolicy {
        public StrictMinimumDiscount {
            if (minimumCents < 1 || minimumCents > 100_000_000L) {
                throw new IllegalArgumentException("minimumCents must be 1-100000000");
            }
        }

        @Override
        public long netCents(long priceCents) {
            requirePrice(priceCents);
            if (priceCents < minimumCents) {
                throw new IllegalArgumentException("Subtype strengthened precondition: minimum " + minimumCents);
            }
            return priceCents;
        }
    }
}
