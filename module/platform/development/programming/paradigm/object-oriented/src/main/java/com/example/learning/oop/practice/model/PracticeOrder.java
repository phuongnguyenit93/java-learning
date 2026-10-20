package com.example.learning.oop.practice.model;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;

/** Aggregate owns line changes, totals and DRAFT/PLACED/CANCELLED lifecycle. */
public final class PracticeOrder {
    public enum Status { DRAFT, PLACED, CANCELLED }

    public record Line(String sku, long unitPriceCents, int quantity) {
        public Line {
            sku = normalize(sku);
            if (unitPriceCents < 1 || unitPriceCents > 1_000_000 || quantity < 1 || quantity > 100) {
                throw new IllegalArgumentException("unitPriceCents must be 1..1000000, quantity 1..100");
            }
        }
        public long subtotal() { return Math.multiplyExact(unitPriceCents, quantity); }
    }

    public record Summary(long subtotalCents, long discountCents, long shippingCents,
                          long payableCents) {}

    private final String id;
    private Status status = Status.DRAFT;
    private final Map<String, Line> lines = new LinkedHashMap<>();

    public PracticeOrder(String id) {
        if (id == null || id.isBlank() || id.length() > 40) {
            throw new IllegalArgumentException("orderId must contain 1..40 characters");
        }
        this.id = id.strip();
    }

    public String id() { return id; }
    public Status status() { return status; }
    public List<Line> lines() { return List.copyOf(lines.values()); }

    public Summary summary() {
        long subtotal = total(lines);
        long discount = subtotal >= 200_000L ? subtotal / 20 : 0L;
        long shipping = subtotal == 0 || subtotal >= 100_000L ? 0L : 15_000L;
        return new Summary(subtotal, discount, shipping,
                Math.addExact(subtotal - discount, shipping));
    }

    public void setLine(String sku, long price, int quantity) {
        draft();
        Line line = new Line(sku, price, quantity);
        Map<String, Line> next = new LinkedHashMap<>(lines);
        next.put(line.sku(), line);
        commit(next);
    }

    public void changeQuantity(String sku, int quantity) {
        draft();
        if (quantity < 0 || quantity > 100) {
            throw new IllegalArgumentException("quantity must be 0..100");
        }
        String key = normalize(sku);
        Line old = lines.get(key);
        if (old == null) throw new NoSuchElementException("unknown SKU");
        Map<String, Line> next = new LinkedHashMap<>(lines);
        if (quantity == 0) next.remove(key);
        else next.put(key, new Line(key, old.unitPriceCents(), quantity));
        commit(next);
    }

    public boolean removeLine(String sku) {
        draft();
        String key = normalize(sku);
        if (!lines.containsKey(key)) return false;
        lines.remove(key);
        return true;
    }

    public void place() {
        draft();
        if (lines.isEmpty()) throw new IllegalStateException("cannot place an empty order");
        status = Status.PLACED;
    }

    public void cancel() {
        if (status == Status.CANCELLED) throw new IllegalStateException("order already cancelled");
        status = Status.CANCELLED;
    }

    private void draft() {
        if (status != Status.DRAFT) throw new IllegalStateException("order is not DRAFT");
    }

    private void commit(Map<String, Line> pending) {
        total(pending); // check full invariant before touching original data
        lines.clear();
        lines.putAll(pending);
    }

    private static long total(Map<String, Line> items) {
        long total = 0;
        for (Line line : items.values()) {
            total = Math.addExact(total, line.subtotal());
            if (total > 10_000_000L) {
                throw new IllegalStateException("order subtotal cannot exceed 10000000 cents");
            }
        }
        return total;
    }

    private static String normalize(String input) {
        if (input == null) throw new IllegalArgumentException("sku is required");
        String sku = input.strip().toUpperCase(Locale.ROOT);
        if (!sku.matches("[A-Z0-9-]{1,24}")) {
            throw new IllegalArgumentException("sku must be 1..24 uppercase letters/digits/hyphens");
        }
        return sku;
    }
}
