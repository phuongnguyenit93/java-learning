package com.example.learning.oop.solid.model;

/** Immutable result of pricing an order; only a valid receipt may be persisted. */
public record CheckoutReceipt(
        String orderId, String policyCode, long subtotalCents,
        long discountCents, long payableCents) {}
