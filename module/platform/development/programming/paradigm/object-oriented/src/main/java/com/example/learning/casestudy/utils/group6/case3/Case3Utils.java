package com.example.learning.casestudy.utils.group6.case3;

import com.example.learning.casestudy.port.group6.case3.Tax;
import java.math.BigDecimal;
import java.math.RoundingMode;

public final class Case3Utils {
    private Case3Utils() {}

    public static BigDecimal rounded(BigDecimal amount, BigDecimal rate) {
        if (amount == null || amount.signum() < 0)
        throw new IllegalArgumentException("Invalid amount");
        return amount.multiply(rate).setScale(2, RoundingMode.HALF_UP);
    }

    public static void contract(Tax implementation) {
        if (!implementation.tax(new BigDecimal("0.45")).equals(new BigDecimal("0.05")))
        throw new AssertionError("Rounding boundary");
        if (!implementation.tax(new BigDecimal("0.44")).equals(new BigDecimal("0.04")))
        throw new AssertionError("Rounding mismatch");
    }
}
