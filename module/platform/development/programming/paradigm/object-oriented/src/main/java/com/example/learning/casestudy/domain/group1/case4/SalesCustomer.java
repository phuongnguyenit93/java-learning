package com.example.learning.casestudy.domain.group1.case4;

import com.example.learning.casestudy.domain.group1.case4.CustomerId;

public final class SalesCustomer {
    private final CustomerId id;
    private int loyaltyPoints;
    public SalesCustomer(CustomerId id) { this.id = id; }
    public void earnPoints(int points) {
        if (points <= 0) throw new IllegalArgumentException("points");
        loyaltyPoints += points;
    }
    public int points() { return loyaltyPoints; }
    public CustomerId id() { return id; }
}
