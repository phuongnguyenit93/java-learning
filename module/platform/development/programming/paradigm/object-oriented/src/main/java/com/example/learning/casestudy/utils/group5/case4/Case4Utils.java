package com.example.learning.casestudy.utils.group5.case4;

import com.example.learning.casestudy.domain.group5.case4.Pricing;

public final class Case4Utils {
    public static void contract(Pricing quote, long expectedSubtotal) {
        assert quote.total() == Math.addExact(expectedSubtotal, quote.fee());
        assert quote.total() >= quote.fee();
    }
}
