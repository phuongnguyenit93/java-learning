package com.example.learning.casestudy.adapter.group4.case2;

import com.example.learning.casestudy.model.group4.case2.Order;
import com.example.learning.casestudy.port.group4.case2.OrderLookup;
import java.util.Map;
import java.util.Optional;

public final class MemoryLookup implements OrderLookup {
    private final Map<String, Order> data = Map.of("O1", new Order("O1"));
    public Optional<Order> find(String id) {
        if (id == null || id.isBlank())
            throw new IllegalArgumentException("id");
        return Optional.ofNullable(data.get(id));
    }
}
