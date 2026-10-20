package com.example.learning.oop.class_chapter.model;

/** A request-local inventory object; mutations can happen only through checked methods. */
public final class InventoryItem {
    public static final int MAX_QUANTITY = 1_000_000;
    public static final long MAX_UNIT_PRICE_VND = 1_000_000_000_000L;

    private final String sku;
    private String name;
    private final long unitPriceVnd;
    private int quantity;

    public InventoryItem(String sku, String name, long unitPriceVnd, int initialQuantity) {
        this.sku = requireText(sku, "sku");
        this.name = requireText(name, "name");
        if (unitPriceVnd < 0 || unitPriceVnd > MAX_UNIT_PRICE_VND) {
            throw new IllegalArgumentException("unitPriceVnd must be between 0 and " + MAX_UNIT_PRICE_VND);
        }
        if (initialQuantity < 0 || initialQuantity > MAX_QUANTITY) {
            throw new IllegalArgumentException("initialQuantity must be between 0 and " + MAX_QUANTITY);
        }
        this.unitPriceVnd = unitPriceVnd;
        this.quantity = initialQuantity;
    }

    public String sku() { return sku; }
    public String name() { return name; }
    public long unitPriceVnd() { return unitPriceVnd; }
    public int quantity() { return quantity; }

    public void rename(String nextName) {
        name = requireText(nextName, "name");
    }

    public void restock(int units) {
        if (units <= 0) {
            throw new IllegalArgumentException("restock units must be positive");
        }
        if (units > MAX_QUANTITY - quantity) {
            throw new IllegalArgumentException("restock exceeds maximum inventory");
        }
        quantity += units;
    }

    public void sell(int units) {
        if (units <= 0) {
            throw new IllegalArgumentException("sell units must be positive");
        }
        if (units > quantity) {
            throw new IllegalStateException("insufficient inventory");
        }
        quantity -= units;
    }

    public long totalValueVnd() {
        return Math.multiplyExact(unitPriceVnd, quantity);
    }

    private static String requireText(String text, String name) {
        if (text == null || text.isBlank() || text.length() > 80) {
            throw new IllegalArgumentException(name + " must contain 1-80 characters");
        }
        return text.strip();
    }
}
