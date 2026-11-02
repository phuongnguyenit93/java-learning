package com.example.learning.casestudy.domain.group5.case4;

public abstract class Pricing {
    private final long mandatoryFee;
    public Pricing(long mandatoryFee) {
        if (mandatoryFee < 0) throw new IllegalArgumentException("fee");
        this.mandatoryFee = mandatoryFee;
    }
    protected abstract long subtotal();
    public final long total() {
        long subtotal = subtotal();
        if (subtotal < 0) throw new IllegalStateException("negative subtotal");
        return Math.addExact(subtotal, mandatoryFee);
    }
    public final long fee() {
        return mandatoryFee;
    }
}
