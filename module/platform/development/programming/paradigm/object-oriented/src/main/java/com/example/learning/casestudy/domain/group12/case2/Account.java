package com.example.learning.casestudy.domain.group12.case2;

public class Account {
    private int balance;
    public Account(int balance) {
        this.balance = balance;
    }
    public int balance() {
        return balance;
    }
    public void debit(int cents) {
        if (cents <= 0 || cents > balance)
        throw new IllegalArgumentException("Invalid debit");
        balance -= cents;
    }
}
