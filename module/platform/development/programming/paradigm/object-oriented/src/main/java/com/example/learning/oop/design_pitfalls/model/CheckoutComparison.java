package com.example.learning.oop.design_pitfalls.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Characterization experiment: deliberately monolithic implementation vs
 * collaborators with separate reasons to change. No real stock or order persistence.
 */
public final class CheckoutComparison {
    private CheckoutComparison() {}

    public record Input(String sku, long unitPriceVnd, int quantity, int availableUnits,
                        boolean member, String deliveryMode) {
        public Input {
            if (sku == null || !sku.matches("[A-Z0-9-]{2,24}")) {
                throw new IllegalArgumentException("sku must be 2..24 uppercase letters/digits/hyphens");
            }
            if (unitPriceVnd < 1 || unitPriceVnd > 1_000_000_000L
                    || quantity < 1 || quantity > 1000
                    || availableUnits < 0 || availableUnits > 1000) {
                throw new IllegalArgumentException("unitPriceVnd 1..1000000000; quantity 1..1000; availableUnits 0..1000");
            }
            if (!"STANDARD".equals(deliveryMode) && !"EXPRESS".equals(deliveryMode)) {
                throw new IllegalArgumentException("deliveryMode must be STANDARD or EXPRESS");
            }
        }
    }

    public record Quote(long subtotalVnd, long discountVnd, long deliveryVnd, long payableVnd) {
        public Quote {
            if (subtotalVnd < 0 || discountVnd < 0 || discountVnd > subtotalVnd
                    || deliveryVnd < 0 || payableVnd < 0) {
                throw new IllegalArgumentException("quote contract violation");
            }
        }
    }

    public record Comparison(Quote legacy, Quote refactored, boolean sameResult,
                             List<String> collaborationTrace) {}

    public static Comparison evaluate(Input input) {
        if (input == null) throw new IllegalArgumentException("checkout input is required");
        Quote oldResult = new LegacyCheckout().quote(input);
        List<String> trace = new ArrayList<>();
        Quote newResult = new CheckoutWorkflow(new StockGuard(), new PriceRules(),
                new DeliveryRules()).quote(input, trace);
        return new Comparison(oldResult, newResult, oldResult.equals(newResult),
                List.copyOf(trace));
    }

    /**
     * Antipattern isolated here only to demonstrate equivalence: one class owns
     * stock checking, pricing/discount and shipping.
     */
    private static final class LegacyCheckout {
        Quote quote(Input order) {
            if (order.quantity() > order.availableUnits()) {
                throw new IllegalStateException("insufficient stock");
            }
            long subtotal = Math.multiplyExact(order.unitPriceVnd(), (long) order.quantity());
            long discount = order.member() ? (subtotal * 5 + 50) / 100 : 0;
            long delivery = "STANDARD".equals(order.deliveryMode()) ? 20_000 : 35_000;
            long payable = Math.addExact(subtotal - discount, delivery);
            return new Quote(subtotal, discount, delivery, payable);
        }
    }

    /** These collaborators are concrete because this bounded use case has no extra variability. */
    private static final class StockGuard {
        void requireAvailable(Input input) {
            if (input.quantity() > input.availableUnits()) {
                throw new IllegalStateException("insufficient stock");
            }
        }
    }

    private static final class PriceRules {
        long subtotal(Input input) {
            return Math.multiplyExact((long) input.quantity(), input.unitPriceVnd());
        }
        long discount(long subtotal, boolean member) {
            return member ? (subtotal * 5 + 50) / 100 : 0L;
        }
    }

    private static final class DeliveryRules {
        long fee(String mode) {
            return switch (mode) {
                case "STANDARD" -> 20_000L;
                case "EXPRESS" -> 35_000L;
                default -> throw new IllegalArgumentException("unsupported deliveryMode");
            };
        }
    }

    private static final class CheckoutWorkflow {
        private final StockGuard stock;
        private final PriceRules prices;
        private final DeliveryRules delivery;

        CheckoutWorkflow(StockGuard stock, PriceRules prices, DeliveryRules delivery) {
            this.stock = stock;
            this.prices = prices;
            this.delivery = delivery;
        }
        Quote quote(Input input, List<String> calls) {
            calls.add("StockGuard.requireAvailable");
            stock.requireAvailable(input);
            calls.add("PriceRules.subtotal");
            long subtotal = prices.subtotal(input);
            calls.add("PriceRules.discount");
            long discount = prices.discount(subtotal, input.member());
            calls.add("DeliveryRules.fee");
            long deliveryFee = delivery.fee(input.deliveryMode());
            calls.add("CheckoutWorkflow.quote");
            return new Quote(subtotal, discount, deliveryFee,
                    Math.addExact(subtotal - discount, deliveryFee));
        }
    }
}
