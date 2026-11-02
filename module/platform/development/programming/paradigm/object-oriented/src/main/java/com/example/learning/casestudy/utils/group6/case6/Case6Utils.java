package com.example.learning.casestudy.utils.group6.case6;

import com.example.learning.casestudy.port.group6.case6.Quote;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

public final class Case6Utils {
    private Case6Utils() {}

    public static BigDecimal normalize(BigDecimal amount, String coupon) {
        if (amount == null || amount.signum() < 0)
        throw new IllegalArgumentException("Invalid amount");
        BigDecimal rate = "SAVE10".equals(coupon) ? new BigDecimal("0.90") : BigDecimal.ONE;
        return amount.multiply(rate).setScale(2, RoundingMode.HALF_UP);
    }

    public static void contract(Quote quote) {
        BigDecimal input = new BigDecimal("0.05");
        BigDecimal first = quote.total(input, "SAVE10");
        if (!first.equals(new BigDecimal("0.05"))) throw new AssertionError("Rounding");
        if (!first.equals(quote.total(input, "SAVE10"))) throw new AssertionError("Side effect");
        if (!input.equals(new BigDecimal("0.05"))) throw new AssertionError("Input modified");
        if (!quote.total(BigDecimal.ZERO, "").equals(new BigDecimal("0.00")))
        throw new AssertionError("Empty coupon");
        for (BigDecimal invalid : List.of(new BigDecimal("-1"))) {
            try {
                quote.total(invalid, null);
                throw new AssertionError("Missing validation");
            } catch (IllegalArgumentException expected) {}
        }
        try {
            quote.total(null, "SAVE10");
            throw new AssertionError("Missing null validation");
        } catch (IllegalArgumentException expected) {}
    }
}
