package com.example.learning.casestudy.domain.group5.case2;

public abstract class Account {
    private long balance;
    public Account(long opening) {
        if (opening < 0) throw new IllegalArgumentException("opening");
        balance = opening;
    }
    public final long balance() {
        return balance;
    }
    public final void withdraw(long amount) {
        if (amount <= 0 || amount > balance)
            throw new IllegalArgumentException("withdraw exceeds balance");
        balance -= amount;
    }
    public final void deposit(long amount) {
        if (amount <= 0) throw new IllegalArgumentException("deposit");
        balance = Math.addExact(balance, amount);
    }
}
