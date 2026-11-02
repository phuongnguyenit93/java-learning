package com.example.learning.casestudy.utils.group2.case11;

public final class Case11Utils {
    public static void check(boolean ok) {
        if (!ok) throw new AssertionError();
    }
    public static void deny(Runnable action) {
        try {
            action.run();
        } catch (SecurityException expected) {
            return;
        }
        throw new AssertionError("Expected access denied");
    }
}
