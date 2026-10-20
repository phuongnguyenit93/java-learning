package com.example.learning.oop.testing.model;

import java.util.Objects;

/**
 * Checkout collaborations observable only through port behavior and outcomes;
 * adapters can be swapped without altering the use-case or opening private state.
 */
public final class CheckoutWorkflow {
    public interface Inventory {
        boolean reserve(String sku, int quantity);
        void release(String sku, int quantity);
    }

    public interface PaymentGateway {
        boolean charge(String orderId, long amountVnd);
    }

    public interface ReceiptSender {
        void send(String orderId);
    }

    public record Order(String id, String sku, int quantity, long unitPriceVnd) {
        public Order {
            if (id == null || !id.matches("[A-Za-z0-9-]{2,32}")) {
                throw new IllegalArgumentException("orderId must contain 2..32 letters/digits/hyphens");
            }
            if (sku == null || !sku.matches("[A-Za-z0-9-]{2,32}")) {
                throw new IllegalArgumentException("sku must contain 2..32 letters/digits/hyphens");
            }
            if (quantity < 1 || quantity > 10_000 || unitPriceVnd < 0) {
                throw new IllegalArgumentException("quantity must be 1..10000 and price nonnegative");
            }
        }

        public long totalVnd() {
            return Math.multiplyExact((long) quantity, unitPriceVnd);
        }
    }

    public record Outcome(String status, long payableVnd) {}

    private final Inventory inventory;
    private final PaymentGateway payment;
    private final ReceiptSender receipts;

    public CheckoutWorkflow(Inventory inventory, PaymentGateway payment, ReceiptSender receipts) {
        this.inventory = Objects.requireNonNull(inventory, "inventory");
        this.payment = Objects.requireNonNull(payment, "payment");
        this.receipts = Objects.requireNonNull(receipts, "receipts");
    }

    public Outcome checkout(Order order) {
        Objects.requireNonNull(order, "order");
        long total = order.totalVnd(); // arithmetic validation before collaborators are called
        if (!inventory.reserve(order.sku(), order.quantity())) {
            return new Outcome("OUT_OF_STOCK", total);
        }
        final boolean charged;
        try {
            charged = payment.charge(order.id(), total);
        } catch (RuntimeException paymentFailure) {
            inventory.release(order.sku(), order.quantity());
            return new Outcome("PAYMENT_ERROR_ROLLED_BACK", total);
        }
        if (!charged) {
            inventory.release(order.sku(), order.quantity());
            return new Outcome("PAYMENT_DECLINED_ROLLED_BACK", total);
        }
        try {
            receipts.send(order.id());
        } catch (RuntimeException notificationFailure) {
            // Payment has succeeded; never claim that a refund happened.
            return new Outcome("PAID_RECEIPT_FAILED", total);
        }
        return new Outcome("SUCCESS", total);
    }
}
