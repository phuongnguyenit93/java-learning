package com.example.learning.oop.encapsulation.model;

import java.util.List;

/** Order lifecycle deliberately uses only CREATED->PAID->SHIPPED or CREATED->CANCELLED. */
public final class LifecycleOrder {
    public enum Status { CREATED, PAID, SHIPPED, CANCELLED }

    public record OrderItem(String sku, int quantity) {
        public OrderItem {
            if (sku == null || !sku.strip().matches("[A-Za-z0-9-]{1,24}")) {
                throw new IllegalArgumentException("sku is required");
            }
            sku = sku.strip();
            if (quantity < 1 || quantity > 1_000_000) {
                throw new IllegalArgumentException("quantity must be 1..1000000");
            }
        }
    }

    private final String orderId;
    private final List<OrderItem> items;
    private Status status = Status.CREATED;

    public LifecycleOrder(String orderId, List<OrderItem> items) {
        if (orderId == null || !orderId.strip().matches("[A-Za-z0-9-]{1,40}")) {
            throw new IllegalArgumentException("orderId must be 1..40 characters");
        }
        if (items == null || items.isEmpty() || items.size() > 25 ||
                items.stream().anyMatch(java.util.Objects::isNull)) {
            throw new IllegalArgumentException("1..25 non-null items are required");
        }
        this.orderId = orderId.strip();
        this.items = List.copyOf(items);
    }

    public String orderId() { return orderId; }
    public List<OrderItem> items() { return items; }
    public synchronized Status status() { return status; }

    public synchronized void markPaid() {
        requireStatus(Status.CREATED, "markPaid");
        // Demonstration assumes the caller was separately authorized to confirm payment.
        status = Status.PAID;
    }

    public synchronized void ship() {
        requireStatus(Status.PAID, "ship");
        status = Status.SHIPPED;
    }

    public synchronized void cancel() {
        requireStatus(Status.CREATED, "cancel");
        status = Status.CANCELLED;
    }

    private void requireStatus(Status required, String operation) {
        if (status != required) {
            throw new IllegalStateException(operation + " not allowed when " + status);
        }
    }
}
