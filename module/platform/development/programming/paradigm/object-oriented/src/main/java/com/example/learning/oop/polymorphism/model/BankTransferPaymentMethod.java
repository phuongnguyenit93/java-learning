package com.example.learning.oop.polymorphism.model;

/** A transfer instruction is not evidence that money reached a bank. */
public final class BankTransferPaymentMethod implements PaymentMethod {
    @Override public String name() { return "BANK"; }
    @Override public PaymentOutcome pay(PaymentCommand command) {
        if (command == null) throw new IllegalArgumentException("command is required");
        return new PaymentOutcome(PaymentOutcome.Status.PENDING,
                "simulated transfer awaiting confirmation for " + command.orderId());
    }
}
