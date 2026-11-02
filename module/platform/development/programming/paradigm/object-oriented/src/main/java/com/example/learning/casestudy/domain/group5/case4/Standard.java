package com.example.learning.casestudy.domain.group5.case4;

public final class Standard extends Pricing {
    private final long amount;
    public Standard(long amount, long fee) {
        super(fee);
        this.amount = amount;
    }
    protected long subtotal() {
        return amount;
    }
}
