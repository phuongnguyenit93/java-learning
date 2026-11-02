package com.example.learning.casestudy.utils.group3.case7;

public final class Case7Utils {
    private Case7Utils() {}

    public static void check(boolean b) {
        if (!b)throw new AssertionError();
    }

    public static void rejected(Runnable r) {
        try {
            r.run();
            throw new AssertionError();
        }
        catch (IllegalArgumentException expected) {
        }
    }

}
