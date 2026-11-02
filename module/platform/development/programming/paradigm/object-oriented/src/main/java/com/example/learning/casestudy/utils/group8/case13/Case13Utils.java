package com.example.learning.casestudy.utils.group8.case13;

import com.example.learning.casestudy.application.group8.case13.CheckoutCalculator;
import com.example.learning.casestudy.port.group8.case13.DiscountPolicy;

public final class Case13Utils {
    private Case13Utils() {
    }

    public static void contract(DiscountPolicy policy) {
        long result = new CheckoutCalculator(policy).total(10_000);
        assert result >= 0 && result <= 10_000;
    }
}
