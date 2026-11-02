package com.example.learning.casestudy.domain.group3.case8;

import com.example.learning.casestudy.port.group3.case8.Fees;

public class Account {
    private long balance = 100;
    public long balance() {
        return balance;
    }
    public void withdraw(long amount, Fees f) {
        if (amount <= 0)throw new IllegalArgumentException();
        long fee = f.calculate(amount);
        long total = Math.addExact(amount, fee);
        if (fee<0 || total>balance)throw new IllegalArgumentException();
        balance -= total;
    }
}
