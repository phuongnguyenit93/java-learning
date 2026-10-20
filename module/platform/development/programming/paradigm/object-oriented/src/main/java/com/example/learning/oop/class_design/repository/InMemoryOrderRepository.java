package com.example.learning.oop.class_design.repository;

import com.example.learning.oop.class_design.model.CheckedOutOrder;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/** Storage responsibility is independent of cart pricing or receipt delivery. */
public final class InMemoryOrderRepository {
    private final Map<String, CheckedOutOrder> orders = new HashMap<>();

    public void save(CheckedOutOrder order) {
        Objects.requireNonNull(order, "order");
        if (orders.putIfAbsent(order.orderId(), order) != null) {
            throw new IllegalStateException("ORDER_EXISTS");
        }
    }

    public CheckedOutOrder find(String orderId) {
        return orders.get(orderId);
    }
}
