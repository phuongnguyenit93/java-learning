package com.example.learning.oop.abstraction.model;

import java.util.Locale;

/**
 * Template Method: validation and the total-fee contract cannot be overridden.
 * Only the variable part of the calculation is implemented by concrete plans.
 */
public abstract class ShippingPlan {
    private final String code;
    private final long baseFeeVnd;

    protected ShippingPlan(String code, long baseFeeVnd) {
        if (code == null || code.isBlank() || baseFeeVnd < 0) {
            throw new IllegalArgumentException("Invalid shipping plan");
        }
        this.code = code;
        this.baseFeeVnd = baseFeeVnd;
    }

    public final Quote quote(int distanceKm, int weightGrams) {
        if (distanceKm <= 0 || weightGrams <= 0) {
            throw new IllegalArgumentException("distanceKm and weightGrams must be positive");
        }
        long billedKilograms = ((long) weightGrams - 1L) / 1000L + 1L;
        long extra = extraFee(distanceKm, billedKilograms);
        if (extra < 0) {
            throw new IllegalStateException("Shipping policy returned a negative extra fee");
        }
        long total = Math.addExact(baseFeeVnd, extra);
        return new Quote(code, distanceKm, weightGrams, billedKilograms,
                baseFeeVnd, extra, total);
    }

    protected abstract long extraFee(int distanceKm, long billedKilograms);

    public static ShippingPlan named(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Shipping plan must be ECONOMY or EXPRESS");
        }
        return switch (value.trim().toUpperCase(Locale.ROOT)) {
            case "ECONOMY" -> new Economy();
            case "EXPRESS" -> new Express();
            default -> throw new IllegalArgumentException("Unknown shipping plan: " + value);
        };
    }

    public record Quote(
            String plan, int distanceKm, int weightGrams, long billedKilograms,
            long baseFeeVnd, long extraFeeVnd, long totalFeeVnd) {
    }

    private static final class Economy extends ShippingPlan {
        private Economy() {
            super("ECONOMY", 10_000L);
        }

        @Override
        protected long extraFee(int distanceKm, long billedKilograms) {
            return Math.multiplyExact(1_000L, distanceKm);
        }
    }

    private static final class Express extends ShippingPlan {
        private Express() {
            super("EXPRESS", 20_000L);
        }

        @Override
        protected long extraFee(int distanceKm, long billedKilograms) {
            long byDistance = Math.multiplyExact(2_000L, distanceKm);
            long byWeight = Math.multiplyExact(3_000L, billedKilograms);
            return Math.addExact(byDistance, byWeight);
        }
    }
}
