package com.example.learning.oop.inheritance.model;

import java.util.ArrayList;
import java.util.List;

/**
 * An intentionally deep inheritance chain; every subclass can augment the
 * parent's distanceFee with super, but never bypass final quote validation.
 */
public class DeliveryPlan {
    private static final long BASE_FEE_VND = 10_000L;

    public final Quote quote(int distanceKm) {
        if (distanceKm <= 0) {
            throw new IllegalArgumentException("distanceKm must be positive");
        }
        List<String> trace = new ArrayList<>();
        long extra = distanceFee(distanceKm, trace);
        if (extra < 0) {
            throw new IllegalStateException("Delivery surcharge must not be negative");
        }
        trace.add("fixed base: +" + BASE_FEE_VND);
        long total = Math.addExact(BASE_FEE_VND, extra);
        return new Quote(total, List.copyOf(trace));
    }

    protected long distanceFee(int distanceKm, List<String> trace) {
        long cost = Math.multiplyExact(distanceKm, 1_000L);
        trace.add("base distance: +" + cost);
        return cost;
    }

    public record Quote(long feeVnd, List<String> trace) {
        public Quote {
            trace = List.copyOf(trace);
        }
    }

    public static class CityDelivery extends DeliveryPlan {
        // Inherits distanceFee without adding a separate behavior.
    }

    public static class PriorityCityDelivery extends CityDelivery {
        @Override
        protected long distanceFee(int distanceKm, List<String> trace) {
            long parentFee = super.distanceFee(distanceKm, trace);
            trace.add("priority surcharge: +5000");
            return Math.addExact(parentFee, 5_000L);
        }
    }

    public static final class HolidayPriorityDelivery extends PriorityCityDelivery {
        @Override
        protected long distanceFee(int distanceKm, List<String> trace) {
            long parentFee = super.distanceFee(distanceKm, trace);
            trace.add("holiday surcharge: +7000");
            return Math.addExact(parentFee, 7_000L);
        }
    }
}
