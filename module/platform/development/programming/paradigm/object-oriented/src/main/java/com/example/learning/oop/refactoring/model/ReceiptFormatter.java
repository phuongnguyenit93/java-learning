package com.example.learning.oop.refactoring.model;

/** Presentation rules change independently from price calculations. */
public final class ReceiptFormatter {
    public String format(Pricing price) {
        return "subtotal=" + price.subtotalVnd()
                + ";discount=" + price.discountVnd()
                + ";shipping=" + price.shippingVnd()
                + ";total=" + price.totalVnd();
    }
}
