package com.example.learning.casestudy.utils.group13.case7;

import com.example.learning.casestudy.domain.group13.case7.Line;

public final class Case7Utils {
    private Case7Utils() {
    }

    public static long usingObjects(Line[] lines){
        long total = 0;
        for (Line x:lines)
        total = Math.addExact(total,
        Math.multiplyExact(x.unitPrice(), x.quantity()));
        return total;
    }

    public static long usingArrays(long[] prices, int[] quantities){
        if (prices.length != quantities.length)
        throw new IllegalArgumentException("length");
        long total = 0;
        for (int i = 0;i<prices.length;i++)
        total = Math.addExact(total,
        Math.multiplyExact(prices[i], quantities[i]));
        return total;
    }

    public static void check(boolean ok){
        if (!ok)throw new AssertionError();
    }
}
