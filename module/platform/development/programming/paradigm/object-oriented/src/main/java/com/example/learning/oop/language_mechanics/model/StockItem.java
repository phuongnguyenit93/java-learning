package com.example.learning.oop.language_mechanics.model;

import java.util.Objects;

/** Domain entity owns stock invariants and safe diagnostics; secrets stay private. */
public final class StockItem {
    private final ProductKey key;
    private final String contactEmail;
    private final String providerToken;
    private int available;

    public StockItem(ProductKey key, int quantity, String contactEmail, String providerToken) {
        this.key = Objects.requireNonNull(key, "key");
        if (quantity < 0) {
            throw new IllegalArgumentException("Initial stock must be nonnegative");
        }
        this.available = quantity;
        this.contactEmail = contactEmail;
        this.providerToken = providerToken;
    }

    /** A query never mutates stock. */
    public int available() {
        return available;
    }

    public void restock(int quantity) {
        requirePositive(quantity);
        available = Math.addExact(available, quantity);
    }

    /** A predicted shortage is a regular business refusal, not an exception. */
    public ReserveOutcome reserve(int quantity) {
        requirePositive(quantity);
        if (available < quantity) {
            return new ReserveDeclined("INSUFFICIENT_STOCK", available);
        }
        available -= quantity;
        return new ReserveAccepted(available);
    }

    private static void requirePositive(int quantity) {
        if (quantity < 1) {
            throw new IllegalArgumentException("Action quantity must be positive");
        }
    }

    public sealed interface ReserveOutcome permits ReserveAccepted, ReserveDeclined {}
    public record ReserveAccepted(int remaining) implements ReserveOutcome {}
    public record ReserveDeclined(String code, int remaining) implements ReserveOutcome {}

    @Override
    public String toString() {
        // Allowlist of safe diagnostics: never embed contactEmail/providerToken.
        return "StockItem{tenantId=" + key.tenantId() + ", sku=" + key.sku()
                + ", available=" + available + "}";
    }
}
