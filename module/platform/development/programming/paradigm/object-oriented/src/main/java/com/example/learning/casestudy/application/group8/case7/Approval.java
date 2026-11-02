package com.example.learning.casestudy.application.group8.case7;

import com.example.learning.casestudy.port.group8.case7.OrderReader;

public final class Approval {
    private final OrderReader orders;
    public Approval(OrderReader orders) { this.orders = orders; }
    public boolean large(String id) {
        return orders.find(id).orElseThrow().cents() >= 100_000;
    }
}
