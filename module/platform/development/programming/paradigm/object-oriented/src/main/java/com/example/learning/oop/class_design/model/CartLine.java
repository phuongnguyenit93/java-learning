package com.example.learning.oop.class_design.model;

/** Value object keeps price/quantity checks beside the money calculation. */
public record CartLine(String sku, int quantity, long unitPriceCents) {
    public CartLine {
        if (sku == null || sku.isBlank() || quantity < 1 || quantity > 10_000
                || unitPriceCents < 0) {
            throw new IllegalArgumentException("Invalid cart line");
        }
        Math.multiplyExact(quantity, unitPriceCents);
    }

    public long subtotalCents() {
        return Math.multiplyExact(quantity, unitPriceCents);
    }
}
