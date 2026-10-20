package com.example.learning.oop.solid.model;

import java.util.List;

/** Order invariants are independent of HTTP, discount rules and storage adapters. */
public record PurchaseOrder(String id, List<Line> lines) {
    public PurchaseOrder {
        if (id == null || !id.matches("[A-Za-z0-9-]{2,32}")) {
            throw new IllegalArgumentException("orderId must be 2..32 letters, digits or hyphens");
        }
        if (lines == null || lines.isEmpty() || lines.size() > 20) {
            throw new IllegalArgumentException("order requires 1..20 lines");
        }
        lines = List.copyOf(lines);
    }

    public long subtotalCents() {
        long total = 0;
        for (Line line : lines) {
            total = Math.addExact(total, line.amountCents());
        }
        return total;
    }

    public record Line(String sku, int quantity, long unitPriceCents) {
        public Line {
            if (sku == null || sku.isBlank() || sku.length() > 32) {
                throw new IllegalArgumentException("sku must contain 1..32 non-blank characters");
            }
            if (quantity <= 0 || quantity > 1_000_000 || unitPriceCents < 0) {
                throw new IllegalArgumentException("quantity must be 1..1000000 and unitPriceCents nonnegative");
            }
        }

        public long amountCents() {
            return Math.multiplyExact((long) quantity, unitPriceCents);
        }
    }
}
