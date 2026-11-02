package com.example.learning.casestudy.application.group3.case7;

public class Transfer {
    private final long limit = 1000;
    private long sent;
    public void execute(long amount) {
        if (amount <= 0 || amount>limit)throw new IllegalArgumentException();
        sent += amount;
    }
    public long sent() {
        return sent;
    }
}
