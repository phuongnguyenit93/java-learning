package com.example.learning.casestudy.domain.group12.case4;

public class Pricing {
    public int total(Order4 order) {
        return order.subtotalCents() - order.discountCents();
    }
}
