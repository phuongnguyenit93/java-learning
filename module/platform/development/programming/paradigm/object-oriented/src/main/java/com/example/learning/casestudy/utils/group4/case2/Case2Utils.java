package com.example.learning.casestudy.utils.group4.case2;

import com.example.learning.casestudy.port.group4.case2.OrderLookup;

public final class Case2Utils {
    private Case2Utils() {}

    public static void contract(OrderLookup lookup) {
        if (!lookup.find("O1").orElseThrow().id().equals("O1"))
            throw new AssertionError("found result");
        if (lookup.find("MISSING").isPresent())
            throw new AssertionError("missing result");
        try {
            lookup.find(null);
            throw new AssertionError("null accepted");
        } catch (IllegalArgumentException expected) { }
    }
}
