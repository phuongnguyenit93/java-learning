package com.example.learning.oop.encapsulation.model;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** State changes only through guarded methods; snapshots never expose the backing map. */
public final class EncapsulatedCart {
    private static final int MAX_LINES = 25;
    private final LinkedHashMap<String, CartLine> lines = new LinkedHashMap<>();

    public EncapsulatedCart(List<CartLine> initialLines) {
        if (initialLines == null || initialLines.size() > MAX_LINES) {
            throw new IllegalArgumentException("initialLines must be a list with up to 25 lines");
        }
        LinkedHashMap<String, CartLine> pending = new LinkedHashMap<>();
        for (CartLine line : initialLines) {
            if (line == null || pending.putIfAbsent(line.sku(), line) != null) {
                throw new IllegalArgumentException("initialLines cannot contain null or duplicate sku");
            }
        }
        validateTotal(pending);
        lines.putAll(pending);
    }

    public void putLine(CartLine newLine) {
        if (newLine == null) throw new IllegalArgumentException("line is required");
        LinkedHashMap<String, CartLine> next = copy();
        next.put(newLine.sku(), newLine);
        if (next.size() > MAX_LINES) throw new IllegalStateException("maximum 25 cart lines");
        commit(next);
    }

    public void changeQuantity(String sku, int nextQuantity) {
        LinkedHashMap<String, CartLine> next = copy();
        CartLine old = next.get(sku);
        if (old == null) throw new IllegalStateException("unknown sku");
        if (nextQuantity < 0 || nextQuantity > 1_000_000) {
            throw new IllegalArgumentException("nextQuantity must be 0..1000000");
        }
        if (nextQuantity == 0) next.remove(sku);
        else next.put(sku, new CartLine(old.sku(), old.unitPriceVnd(), nextQuantity));
        commit(next);
    }

    public void removeLine(String sku) {
        LinkedHashMap<String, CartLine> next = copy();
        if (next.remove(sku) == null) throw new IllegalStateException("unknown sku");
        commit(next);
    }

    public List<CartLine> lines() {
        return List.copyOf(lines.values());
    }

    public long totalVnd() {
        return validateTotal(lines);
    }

    private LinkedHashMap<String, CartLine> copy() {
        return new LinkedHashMap<>(lines);
    }

    private void commit(Map<String, CartLine> next) {
        // Compute and check before touching the real state (including overflow).
        validateTotal(next);
        lines.clear();
        lines.putAll(next);
    }

    private static long validateTotal(Map<String, CartLine> data) {
        long sum = 0;
        for (CartLine line : data.values()) {
            sum = Math.addExact(sum, line.subtotalVnd());
        }
        return sum;
    }
}
