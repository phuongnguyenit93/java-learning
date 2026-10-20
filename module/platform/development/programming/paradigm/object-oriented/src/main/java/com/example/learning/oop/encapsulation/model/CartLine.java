package com.example.learning.oop.encapsulation.model;

/** Immutable value object: no reference to a mutable element can escape. */
public record CartLine(String sku, long unitPriceVnd, int quantity) {
    public CartLine {
        if (sku == null || !sku.strip().matches("[A-Za-z0-9-]{1,24}")) {
            throw new IllegalArgumentException("sku must be 1-24 ASCII letters, digits or hyphens");
        }
        sku = sku.strip();
        if (unitPriceVnd < 0 || unitPriceVnd > 1_000_000_000_000L) {
            throw new IllegalArgumentException("unitPriceVnd must be 0..1000000000000");
        }
        if (quantity < 1 || quantity > 1_000_000) {
            throw new IllegalArgumentException("quantity must be 1..1000000");
        }
        Math.multiplyExact(unitPriceVnd, (long) quantity);
    }

    public long subtotalVnd() {
        return Math.multiplyExact(unitPriceVnd, (long) quantity);
    }
}
