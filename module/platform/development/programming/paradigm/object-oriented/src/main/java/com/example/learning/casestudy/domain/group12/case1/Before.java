package com.example.learning.casestudy.domain.group12.case1;

public class Before {
    public int payable(int baseCents, int discountCents) {
        if (baseCents < 0 || discountCents < 0 || discountCents > baseCents)
        throw new IllegalArgumentException();
        return calculate(baseCents, discountCents);
    }
    private int calculate(int base, int discount) {
        return base - discount;
    }
}
