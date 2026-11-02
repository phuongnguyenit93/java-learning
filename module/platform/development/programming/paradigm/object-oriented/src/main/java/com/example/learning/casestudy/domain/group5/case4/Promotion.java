package com.example.learning.casestudy.domain.group5.case4;

public final class Promotion extends Pricing {
    private final long amount;
    public Promotion(long amount, long fee) {
        super(fee);
        this.amount = amount;
    }
    protected long subtotal() {
        return amount * 9 / 10;
    }
}
