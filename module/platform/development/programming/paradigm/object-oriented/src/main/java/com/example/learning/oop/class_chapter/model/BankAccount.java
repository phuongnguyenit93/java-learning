package com.example.learning.oop.class_chapter.model;

import java.util.Objects;

/** Single-request, single-thread teaching model. Not a real banking service. */
public final class BankAccount {
    private final String accountNumber;
    private final String holderName;
    private long balanceVnd;

    public BankAccount(String accountNumber, String holderName, long openingBalanceVnd) {
        if (accountNumber == null || !accountNumber.matches("[A-Z0-9]{6,20}")) {
            throw new IllegalArgumentException("accountNumber must be 6-20 uppercase letters or digits");
        }
        if (holderName == null || holderName.isBlank() || holderName.length() > 80) {
            throw new IllegalArgumentException("holderName must contain 1-80 characters");
        }
        if (openingBalanceVnd < 0) {
            throw new IllegalArgumentException("openingBalanceVnd cannot be negative");
        }
        this.accountNumber = accountNumber;
        this.holderName = holderName.strip();
        this.balanceVnd = openingBalanceVnd;
    }

    public String accountNumber() { return accountNumber; }
    public String holderName() { return holderName; }
    public long balanceVnd() { return balanceVnd; }

    public void deposit(long amountVnd) {
        requirePositive(amountVnd);
        balanceVnd = Math.addExact(balanceVnd, amountVnd);
    }

    public void withdraw(long amountVnd) {
        requirePositive(amountVnd);
        if (amountVnd > balanceVnd) {
            throw new IllegalStateException("insufficient funds");
        }
        balanceVnd -= amountVnd;
    }

    public void transferTo(BankAccount destination, long amountVnd) {
        Objects.requireNonNull(destination, "destination");
        requirePositive(amountVnd);
        if (destination == this || destination.accountNumber.equals(accountNumber)) {
            throw new IllegalArgumentException("source and destination must differ");
        }
        if (amountVnd > balanceVnd) {
            throw new IllegalStateException("insufficient funds");
        }
        // Check overflow before mutating either object: a rejected request is atomic here.
        long nextDestination = Math.addExact(destination.balanceVnd, amountVnd);
        balanceVnd -= amountVnd;
        destination.balanceVnd = nextDestination;
    }

    private static void requirePositive(long amountVnd) {
        if (amountVnd <= 0) {
            throw new IllegalArgumentException("amountVnd must be positive");
        }
    }
}
