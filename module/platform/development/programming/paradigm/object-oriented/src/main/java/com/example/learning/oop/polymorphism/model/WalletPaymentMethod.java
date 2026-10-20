package com.example.learning.oop.polymorphism.model;

/** Request-local mutable balance with an invariant: never negative. */
public final class WalletPaymentMethod implements PaymentMethod {
    private long balanceVnd;
    public WalletPaymentMethod(long initialBalanceVnd) {
        if (initialBalanceVnd < 0 || initialBalanceVnd > 1_000_000_000_000L) {
            throw new IllegalArgumentException("initialWalletBalanceVnd must be 0..1000000000000");
        }
        balanceVnd = initialBalanceVnd;
    }
    public long balanceVnd() { return balanceVnd; }
    @Override public String name() { return "WALLET"; }
    @Override public PaymentOutcome pay(PaymentCommand command) {
        if (command == null) throw new IllegalArgumentException("command is required");
        if (command.amountVnd() > balanceVnd) {
            return new PaymentOutcome(PaymentOutcome.Status.DECLINED, "insufficient simulated wallet balance");
        }
        balanceVnd -= command.amountVnd();
        return new PaymentOutcome(PaymentOutcome.Status.PAID, "simulated wallet debit");
    }
}
