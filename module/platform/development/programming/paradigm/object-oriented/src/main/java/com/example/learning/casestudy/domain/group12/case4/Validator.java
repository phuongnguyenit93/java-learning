package com.example.learning.casestudy.domain.group12.case4;

public class Validator {
    public void verify(Order4 order) {
        if (order.subtotalCents() < 0 || order.discountCents() < 0
        || order.discountCents() > order.subtotalCents())
        throw new IllegalArgumentException();
    }
}
