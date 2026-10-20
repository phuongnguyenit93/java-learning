package com.example.learning.oop.testing.repository;

import com.example.learning.oop.testing.model.CheckoutWorkflow.Inventory;
import com.example.learning.oop.testing.model.CheckoutWorkflow.PaymentGateway;
import com.example.learning.oop.testing.model.CheckoutWorkflow.ReceiptSender;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** In-memory fake, configurable stub and recording mock for an isolated run. */
public final class CheckoutTestDoubles {
    private CheckoutTestDoubles() {}

    public static final class FakeInventory implements Inventory {
        private final String sku;
        private final List<String> events;
        private int available;

        public FakeInventory(String sku, int stock, List<String> events) {
            if (stock < 0 || stock > 10_000) {
                throw new IllegalArgumentException("stock must be 0..10000");
            }
            this.sku = Objects.requireNonNull(sku);
            this.available = stock;
            this.events = Objects.requireNonNull(events);
        }

        @Override
        public boolean reserve(String requestedSku, int quantity) {
            boolean accepted = sku.equals(requestedSku) && quantity > 0 && quantity <= available;
            events.add("inventory.reserve:" + (accepted ? "ACCEPTED" : "REJECTED"));
            if (accepted) {
                available -= quantity;
            }
            return accepted;
        }

        @Override
        public void release(String requestedSku, int quantity) {
            if (!sku.equals(requestedSku) || quantity < 1 || available > 10_000 - quantity) {
                throw new IllegalStateException("invalid inventory release");
            }
            available += quantity;
            events.add("inventory.release");
        }

        public int available() {
            return available;
        }
    }

    public static final class StubPayment implements PaymentGateway {
        private final String mode;
        private final List<String> events;
        private int attempts;

        public StubPayment(String mode, List<String> events) {
            if (!List.of("APPROVE", "DECLINE", "ERROR").contains(mode)) {
                throw new IllegalArgumentException("paymentMode must be APPROVE/DECLINE/ERROR");
            }
            this.mode = mode;
            this.events = Objects.requireNonNull(events);
        }

        @Override
        public boolean charge(String orderId, long amountVnd) {
            attempts++;
            events.add("payment.charge:" + mode);
            if (mode.equals("ERROR")) {
                throw new IllegalStateException("simulated payment gateway outage");
            }
            return mode.equals("APPROVE");
        }

        public int attempts() {
            return attempts;
        }
    }

    public static final class RecordingSender implements ReceiptSender {
        private final String mode;
        private final List<String> events;
        private final List<String> sent = new ArrayList<>();

        public RecordingSender(String mode, List<String> events) {
            if (!List.of("SEND", "ERROR").contains(mode)) {
                throw new IllegalArgumentException("receiptMode must be SEND/ERROR");
            }
            this.mode = mode;
            this.events = Objects.requireNonNull(events);
        }

        @Override
        public void send(String orderId) {
            events.add("receipt.send:" + mode);
            if (mode.equals("ERROR")) {
                throw new IllegalStateException("simulated mail delivery failure");
            }
            sent.add(orderId);
        }

        public List<String> sent() {
            return List.copyOf(sent);
        }
    }
}
