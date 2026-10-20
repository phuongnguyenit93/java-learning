package com.example.learning.oop.solid.repository;

import com.example.learning.oop.solid.model.CheckoutReceipt;
import com.example.learning.oop.solid.model.OrderPorts.ReceiptSender;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Simulates outbound receipts as an observable per-experiment event log. */
public final class MemoryReceiptSender implements ReceiptSender {
    private final List<Notice> notices = new ArrayList<>();

    @Override
    public void send(CheckoutReceipt receipt) {
        Objects.requireNonNull(receipt, "receipt");
        notices.add(new Notice(receipt.orderId(), receipt.payableCents()));
    }

    public List<Notice> notices() {
        return List.copyOf(notices);
    }

    public record Notice(String orderId, long payableCents) {}
}
