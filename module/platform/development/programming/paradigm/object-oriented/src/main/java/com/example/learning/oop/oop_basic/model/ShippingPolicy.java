package com.example.learning.oop.oop_basic.model;

/**
 * Same message, distinct concrete implementations: class-based polymorphism.
 * Illustrative fee rules, not real carrier prices.
 */
public interface ShippingPolicy {
    String name();

    long feeCents(int distanceKm);

    final class Pickup implements ShippingPolicy {
        @Override
        public String name() {
            return "PICKUP";
        }

        @Override
        public long feeCents(int distanceKm) {
            return 0;
        }
    }

    final class Courier implements ShippingPolicy {
        @Override
        public String name() {
            return "COURIER";
        }

        @Override
        public long feeCents(int distanceKm) {
            return Math.addExact(1_500, Math.multiplyExact(400, distanceKm));
        }
    }
}
