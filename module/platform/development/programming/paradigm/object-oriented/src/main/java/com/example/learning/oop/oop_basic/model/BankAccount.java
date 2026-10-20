package com.example.learning.oop.oop_basic.model;

/**
 * In-memory entity. Its balance cannot be changed without passing an invariant.
 * Instances are never shared between HTTP requests.
 */
public final class BankAccount {
    private final String accountId;
    private long balanceCents;

    public BankAccount(String accountId, long openingBalanceCents) {
        if (accountId == null || !accountId.matches("[A-Za-z0-9_-]{1,40}")) {
            throw new IllegalArgumentException("accountId must contain 1-40 letters, digits, '_' or '-'");
        }
        if (openingBalanceCents < 0) {
            throw new IllegalArgumentException("openingBalanceCents cannot be negative");
        }
        this.accountId = accountId;
        this.balanceCents = openingBalanceCents;
    }

    public String accountId() {
        return accountId;
    }

    public long balanceCents() {
        return balanceCents;
    }

    public void deposit(long amountCents) {
        requirePositive(amountCents);
        // Compute first: overflow leaves the existing state untouched.
        long next = Math.addExact(balanceCents, amountCents);
        balanceCents = next;
    }

    public boolean withdraw(long amountCents) {
        requirePositive(amountCents);
        if (amountCents > balanceCents) {
            return false;
        }
        balanceCents -= amountCents;
        return true;
    }

    private static void requirePositive(long amountCents) {
        if (amountCents <= 0) {
            throw new IllegalArgumentException("amountCents must be positive");
        }
    }
}
