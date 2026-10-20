package com.example.learning.oop.class_design.model;

import java.util.Objects;

/** Entity identity: account ID persists while balance changes. */
public final class BankAccount {
    private final String id;
    private Money balance;

    public BankAccount(String id, Money openingBalance) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Account id must be nonblank");
        }
        this.id = id;
        balance = Objects.requireNonNull(openingBalance, "openingBalance");
    }

    public String id() { return id; }
    /** A query: reading the balance never changes it. */
    public Money balance() { return balance; }

    /** A command: verify both updated values before committing either balance. */
    public void transferTo(BankAccount recipient, Money amount) {
        Objects.requireNonNull(recipient, "recipient");
        Objects.requireNonNull(amount, "amount");
        if (this == recipient || id.equals(recipient.id)) {
            throw new IllegalArgumentException("Cannot transfer to same account");
        }
        if (amount.cents() == 0) {
            throw new IllegalArgumentException("Transfer amount must be positive");
        }
        Money debited = balance.minus(amount);
        Money credited = recipient.balance.plus(amount);
        balance = debited;
        recipient.balance = credited;
    }
}
