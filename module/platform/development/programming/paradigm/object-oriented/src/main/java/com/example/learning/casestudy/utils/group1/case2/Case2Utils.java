package com.example.learning.casestudy.utils.group1.case2;

import com.example.learning.casestudy.model.group1.case2.Order2;

public final class Case2Utils {
    private Case2Utils() {}

    public static void check(boolean ok) { if (!ok) throw new AssertionError(); }

    public static void rejects(java.util.function.Function<Order2,String> fn) {
        try {
            fn.apply(new Order2("", -1));
            throw new AssertionError("expected validation failure");
        } catch (IllegalArgumentException expected) { }
    }
}
