package com.example.learning.casestudy.domain.group1.case11;

import com.example.learning.casestudy.domain.group1.case11.Status11;

public final class Order11 {
    private final String orderId;
    private Status11 status = Status11.NEW;
    public Order11(String id) {
        if (id == null || id.isBlank())
            throw new IllegalArgumentException("orderId");
        orderId = id;
    }
    public String id() { return orderId; }
    public Status11 status() { return status; }
    public void markPaid() {
        if (status != Status11.NEW)
            throw new IllegalStateException("invalid transition");
        status = Status11.PAID;
    }
}
