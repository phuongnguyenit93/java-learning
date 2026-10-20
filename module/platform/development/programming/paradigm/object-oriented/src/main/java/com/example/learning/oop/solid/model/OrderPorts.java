package com.example.learning.oop.solid.model;

import java.util.List;

/** Small ports: the checkout writer does not need read/report operations. */
public final class OrderPorts {
    private OrderPorts() {}

    public interface OrderWriter {
        void save(CheckoutReceipt receipt);
    }

    public interface OrderReader {
        List<CheckoutReceipt> findAll();
    }

    public interface ReceiptSender {
        void send(CheckoutReceipt receipt);
    }
}
