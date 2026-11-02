package com.example.learning.casestudy.domain.group1.case3;

public final class Account {
    private long balance;
    public Account(long opening) {
        if (opening < 0) throw new IllegalArgumentException("opening");
        balance = opening;
    }
    public long balance() { return balance; }
    public void withdraw(long amount) {
        if (amount <= 0 || amount > balance)
            throw new IllegalArgumentException("amount");
        balance -= amount;
    }
    public void deposit(long amount) {
        if (amount <= 0) throw new IllegalArgumentException("amount");
        balance = Math.addExact(balance, amount);
    }
}
