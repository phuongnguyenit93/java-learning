package com.example.learning.oop.encapsulation.model;

/** Invariant: 0 <= reserved <= onHand <= MAX_UNITS, even after rejected actions. */
public final class StockLedger {
    public static final int MAX_UNITS = 1_000_000;
    private final String sku;
    private int onHand;
    private int reserved;

    public StockLedger(String sku, int initialOnHand, int initialReserved) {
        if (sku == null || !sku.strip().matches("[A-Za-z0-9-]{1,24}")) {
            throw new IllegalArgumentException("sku must be 1-24 ASCII letters, digits or hyphens");
        }
        if (initialOnHand < 0 || initialOnHand > MAX_UNITS ||
                initialReserved < 0 || initialReserved > initialOnHand) {
            throw new IllegalArgumentException("require 0 <= reserved <= onHand <= 1000000");
        }
        this.sku = sku.strip();
        this.onHand = initialOnHand;
        this.reserved = initialReserved;
    }

    public String sku() { return sku; }
    public int onHand() { return onHand; }
    public int reserved() { return reserved; }
    public int available() { return onHand - reserved; }

    public void restock(int amount) {
        positive(amount);
        if (amount > MAX_UNITS - onHand) throw new IllegalStateException("stock limit exceeded");
        onHand += amount;
    }

    public void reserve(int amount) {
        positive(amount);
        if (amount > available()) throw new IllegalStateException("insufficient available units");
        reserved += amount;
    }

    public void release(int amount) {
        positive(amount);
        if (amount > reserved) throw new IllegalStateException("release exceeds reserved units");
        reserved -= amount;
    }

    public void ship(int amount) {
        positive(amount);
        if (amount > reserved) throw new IllegalStateException("cannot ship more than reserved");
        // Both checks have succeeded; no observable invalid intermediate state.
        onHand -= amount;
        reserved -= amount;
    }

    private static void positive(int amount) {
        if (amount <= 0) throw new IllegalArgumentException("amount must be positive");
    }
}
