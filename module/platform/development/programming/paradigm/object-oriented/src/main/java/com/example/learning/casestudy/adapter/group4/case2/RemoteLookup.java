package com.example.learning.casestudy.adapter.group4.case2;

import com.example.learning.casestudy.model.group4.case2.Order;
import com.example.learning.casestudy.port.group4.case2.OrderLookup;
import java.util.Optional;

public final class RemoteLookup implements OrderLookup {
    public Optional<Order> find(String id) {
        if (id == null || id.isBlank())
            throw new IllegalArgumentException("id");
        // Fake remote result: chỉ O1 có dữ liệu.
        return "O1".equals(id) ? Optional.of(new Order("O1"))
                                : Optional.empty();
    }
}
