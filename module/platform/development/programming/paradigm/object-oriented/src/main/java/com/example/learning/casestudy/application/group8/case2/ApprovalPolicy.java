package com.example.learning.casestudy.application.group8.case2;

import com.example.learning.casestudy.domain.group8.case2.Order2;
import com.example.learning.casestudy.port.group8.case2.OrderRepository;

public final class ApprovalPolicy {
    private final OrderRepository orders;
    public ApprovalPolicy(OrderRepository orders) { this.orders = orders; }
    public boolean requiresReview(String id) {
        Order2 order = orders.find(id).orElseThrow();
        return order.cents() >= 100_000;
    }
}
