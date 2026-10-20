package com.example.learning.oop.interface_chapter.model;

/** Simulated payment command; amount is an integer number of VND (not floating point). */
public record PaymentCommand(String requestId, String orderId, long amountVnd) {
    public PaymentCommand {
        if (requestId == null || !requestId.matches("[A-Za-z0-9-]{3,36}")) {
            throw new IllegalArgumentException("requestId must be 3..36 alphanumeric/hyphen characters");
        }
        if (orderId == null || !orderId.matches("[A-Za-z0-9-]{3,36}")) {
            throw new IllegalArgumentException("orderId must be 3..36 alphanumeric/hyphen characters");
        }
        if (amountVnd <= 0 || amountVnd > 1_000_000_000_000L) {
            throw new IllegalArgumentException("amountVnd must be 1..1000000000000");
        }
    }
}
