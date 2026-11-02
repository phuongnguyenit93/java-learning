package com.example.learning.casestudy.domain.group2.case1;

public record Payment1(String reference, Money money) {
    public Payment1 {
        if (reference == null || reference.isBlank() || money == null)
            throw new IllegalArgumentException("Invalid payment");
    }
}
