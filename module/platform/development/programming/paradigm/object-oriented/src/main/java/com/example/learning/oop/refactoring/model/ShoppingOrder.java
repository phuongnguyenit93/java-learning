package com.example.learning.oop.refactoring.model;

import java.util.List;
import java.util.Objects;

/** Encapsulates immutable input snapshot and the sum-of-lines behavior. */
public final class ShoppingOrder {
    private final List<OrderLine> lines;
    private final boolean vip;

    public ShoppingOrder(List<OrderLine> lines, boolean vip) {
        Objects.requireNonNull(lines, "lines");
        if (lines.isEmpty() || lines.size() > 100) {
            throw new IllegalArgumentException("Order requires 1..100 lines");
        }
        this.lines = List.copyOf(lines);
        this.vip = vip;
    }

    public boolean isVip() {
        return vip;
    }

    public List<OrderLine> linesSnapshot() {
        return lines;
    }

    public long subtotalVnd() {
        long total = 0L;
        for (OrderLine line : lines) {
            total = Math.addExact(total, line.lineAmountVnd());
        }
        return total;
    }
}
