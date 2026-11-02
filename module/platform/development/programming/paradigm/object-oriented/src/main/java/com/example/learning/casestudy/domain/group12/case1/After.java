package com.example.learning.casestudy.domain.group12.case1;

public class After {
    public int payable(int baseCents, int discountCents) {
        if (baseCents < 0 || discountCents < 0 || discountCents > baseCents)
        throw new IllegalArgumentException();
        return Math.subtractExact(baseCents, discountCents);
    }

    // Không còn private calculate().
}
