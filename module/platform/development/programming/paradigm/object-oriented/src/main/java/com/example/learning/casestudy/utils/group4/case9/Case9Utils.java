package com.example.learning.casestudy.utils.group4.case9;

import com.example.learning.casestudy.port.group4.case9.Calculator;

public final class Case9Utils {
    private Case9Utils() {}

    public static void contract(Calculator c) {
        if (c.evaluate(0) != 1 || c.evaluate(2) != 3)
            throw new AssertionError("result");
        try {
            c.evaluate(null);
            throw new AssertionError("null accepted");
        } catch (IllegalArgumentException expected) { }
    }
}
