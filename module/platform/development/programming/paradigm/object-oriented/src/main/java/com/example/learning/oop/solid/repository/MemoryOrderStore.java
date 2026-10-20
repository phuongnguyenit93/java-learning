package com.example.learning.oop.solid.repository;

import com.example.learning.oop.solid.model.CheckoutReceipt;
import com.example.learning.oop.solid.model.OrderPorts.OrderReader;
import com.example.learning.oop.solid.model.OrderPorts.OrderWriter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** New per experiment; no database, shared state or durable writes. */
public final class MemoryOrderStore implements OrderWriter, OrderReader {
    private final Map<String, CheckoutReceipt> orders = new LinkedHashMap<>();

    @Override
    public void save(CheckoutReceipt receipt) {
        Objects.requireNonNull(receipt, "receipt");
        if (orders.putIfAbsent(receipt.orderId(), receipt) != null) {
            throw new IllegalStateException("duplicate orderId: " + receipt.orderId());
        }
    }

    @Override
    public List<CheckoutReceipt> findAll() {
        return List.copyOf(new ArrayList<>(orders.values()));
    }
}
