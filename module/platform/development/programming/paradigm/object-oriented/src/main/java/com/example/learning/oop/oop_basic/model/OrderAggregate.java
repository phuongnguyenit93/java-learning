package com.example.learning.oop.oop_basic.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Small stateful aggregate demonstrating where business invariants belong.
 * No database, payment gateway, concurrent update or distributed transaction.
 */
public final class OrderAggregate {
    public enum Status {
        DRAFT, CONFIRMED, CANCELLED
    }

    public record Line(String sku, int quantity, long unitPriceCents) {
        public Line {
            if (sku == null || !sku.matches("[A-Za-z0-9_-]{1,40}")) {
                throw new IllegalArgumentException("sku must contain 1-40 letters, digits, '_' or '-'");
            }
            if (quantity <= 0 || quantity > 10_000) {
                throw new IllegalArgumentException("quantity must be between 1 and 10000");
            }
            if (unitPriceCents < 0) {
                throw new IllegalArgumentException("unitPriceCents cannot be negative");
            }
            Math.multiplyExact(quantity, unitPriceCents);
        }

        public long subtotalCents() {
            return Math.multiplyExact(quantity, unitPriceCents);
        }
    }

    private final String orderId;
    private final List<Line> lines = new ArrayList<>();
    private Status status = Status.DRAFT;
    private long totalCents;

    public OrderAggregate(String orderId) {
        if (orderId == null || !orderId.matches("[A-Za-z0-9_-]{1,40}")) {
            throw new IllegalArgumentException("orderId must contain 1-40 letters, digits, '_' or '-'");
        }
        this.orderId = orderId;
    }

    public String orderId() {
        return orderId;
    }

    public Status status() {
        return status;
    }

    public List<Line> lines() {
        return List.copyOf(lines);
    }

    public long totalCents() {
        return totalCents;
    }

    public void addLine(String sku, int quantity, long unitPriceCents) {
        requireDraft();
        Line line = new Line(sku, quantity, unitPriceCents);
        long nextTotal = Math.addExact(totalCents, line.subtotalCents());
        // Validate every input and arithmetic operation before mutating.
        lines.add(line);
        totalCents = nextTotal;
    }

    public void confirm() {
        requireDraft();
        if (lines.isEmpty()) {
            throw new IllegalStateException("Cannot confirm an empty order");
        }
        status = Status.CONFIRMED;
    }

    public void cancel() {
        requireDraft();
        status = Status.CANCELLED;
    }

    private void requireDraft() {
        if (status != Status.DRAFT) {
            throw new IllegalStateException("Order must be DRAFT; actual status: " + status);
        }
    }
}
