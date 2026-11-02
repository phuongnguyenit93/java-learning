package com.example.learning.casestudy.domain.group8.case8;


public final class InvoiceCalculator {
    public long total(long subtotalCents) {
        if (subtotalCents < 0) throw new IllegalArgumentException("subtotal");
        long tax = Math.multiplyExact(subtotalCents, 10) / 100;
        return Math.addExact(subtotalCents, tax);
    }
}
