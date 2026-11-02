package com.example.learning.casestudy.utils.group3.case1;

public final class Case1Utils {
    private Case1Utils() {}

    public static void check(boolean b) {
        if (!b)throw new AssertionError();
    }

    public static void fails(Runnable r) {
        try {
            r.run();
            throw new AssertionError("expected failure");
        }
        catch (IllegalStateException expected) {
        }
    }

}
