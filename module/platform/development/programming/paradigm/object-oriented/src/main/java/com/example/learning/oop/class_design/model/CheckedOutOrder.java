package com.example.learning.oop.class_design.model;

import java.util.List;

/** Persisted order snapshot: never points at mutable ShoppingCart internals. */
public record CheckedOutOrder(String orderId, List<CartLine> lines, long subtotalCents,
                              long discountCents, long totalCents) {
    public CheckedOutOrder {
        if (orderId == null || orderId.isBlank() || lines == null || lines.isEmpty()
                || subtotalCents < 0 || discountCents < 0 || totalCents < 0
                || subtotalCents - discountCents != totalCents) {
            throw new IllegalArgumentException("Invalid order");
        }
        lines = List.copyOf(lines);
    }
}
