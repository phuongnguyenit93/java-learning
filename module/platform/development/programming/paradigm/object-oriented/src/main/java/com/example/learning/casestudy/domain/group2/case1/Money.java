package com.example.learning.casestudy.domain.group2.case1;

public record Money(long minorUnits) {
    public Money {
        if (minorUnits <= 0)
            throw new IllegalArgumentException("Amount must be positive");
    }
}
