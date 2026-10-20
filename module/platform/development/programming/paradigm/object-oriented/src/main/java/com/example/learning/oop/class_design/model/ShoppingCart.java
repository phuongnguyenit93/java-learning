package com.example.learning.oop.class_design.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** High cohesion: manages valid line items and their subtotal only. */
public final class ShoppingCart {
    private final List<CartLine> lines = new ArrayList<>();

    public void add(CartLine line) {
        lines.add(Objects.requireNonNull(line, "line"));
    }

    public long subtotalCents() {
        long total = 0;
        for (CartLine line : lines) {
            total = Math.addExact(total, line.subtotalCents());
        }
        return total;
    }

    public List<CartLine> snapshot() {
        return List.copyOf(lines);
    }
}
