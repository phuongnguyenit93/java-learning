package com.example.learning.oop.refactoring.model;

/** Price ownership moves to the line: no service needs to know multiplication details. */
public record OrderLine(String sku, long unitPriceVnd, int quantity) {
    public OrderLine {
        if (sku == null || sku.isBlank() || quantity < 1 || unitPriceVnd < 0) {
            throw new IllegalArgumentException("sku must be nonblank, quantity positive, price nonnegative");
        }
    }

    public long lineAmountVnd() {
        return Math.multiplyExact(unitPriceVnd, quantity);
    }
}
