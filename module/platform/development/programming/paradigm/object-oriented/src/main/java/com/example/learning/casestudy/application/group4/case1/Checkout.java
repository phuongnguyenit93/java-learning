package com.example.learning.casestudy.application.group4.case1;

import com.example.learning.casestudy.port.group4.case1.PaymentGateway;

public final class Checkout {
    private final PaymentGateway payment;
    public Checkout(PaymentGateway payment) { this.payment = payment; }
    public boolean pay(String id, long cents) {
        if (id == null || id.isBlank() || cents <= 0)
            throw new IllegalArgumentException("invalid order");
        return payment.capture(id, cents);
    }
}
