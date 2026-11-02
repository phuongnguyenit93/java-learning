package com.example.learning.casestudy.utils.group1.case3;

public final class Case3Utils {
    private Case3Utils() {}

    public static void check(boolean ok) { if (!ok) throw new AssertionError(); }

    public static void rejects(Runnable action) {
        try {
            action.run();
        } catch (IllegalArgumentException expected) { return; }
        throw new AssertionError("should reject");
    }
}
