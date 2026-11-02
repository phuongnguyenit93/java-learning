package com.example.learning.casestudy.domain.group3.case8;

import com.example.learning.casestudy.port.group3.case8.Fees;

public class BadAccount {
    public long balance = 100;
    public void withdraw(long amount, Fees f) {
        balance -= amount;
        balance -= f.calculate(amount);
    }
}
