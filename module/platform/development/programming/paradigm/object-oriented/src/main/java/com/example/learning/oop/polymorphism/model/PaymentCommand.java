package com.example.learning.oop.polymorphism.model;

public record PaymentCommand(String orderId, long amountVnd) {
    public PaymentCommand {
        if (orderId == null || !orderId.matches("[A-Z0-9-]{3,36}")) {
            throw new IllegalArgumentException("orderId must be 3..36 uppercase letters, digits or hyphens");
        }
        if (amountVnd < 1 || amountVnd > 1_000_000_000_000L) {
            throw new IllegalArgumentException("amountVnd must be 1..1000000000000");
        }
    }
}
