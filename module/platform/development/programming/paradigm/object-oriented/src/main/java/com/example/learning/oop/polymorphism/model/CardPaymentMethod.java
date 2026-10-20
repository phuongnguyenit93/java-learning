package com.example.learning.oop.polymorphism.model;

/** Simulated per-operation card limit; no actual card data or remote charging. */
public final class CardPaymentMethod implements PaymentMethod {
    private final long perPaymentLimitVnd;
    public CardPaymentMethod(long perPaymentLimitVnd) {
        if (perPaymentLimitVnd < 1 || perPaymentLimitVnd > 1_000_000_000_000L) {
            throw new IllegalArgumentException("cardLimitVnd must be 1..1000000000000");
        }
        this.perPaymentLimitVnd = perPaymentLimitVnd;
    }
    @Override public String name() { return "CARD"; }
    @Override public PaymentOutcome pay(PaymentCommand command) {
        if (command == null) throw new IllegalArgumentException("command is required");
        return command.amountVnd() <= perPaymentLimitVnd
                ? new PaymentOutcome(PaymentOutcome.Status.PAID, "simulated card authorization")
                : new PaymentOutcome(PaymentOutcome.Status.DECLINED, "simulated card limit exceeded");
    }
}
