package com.example.learning.casestudy.utils.group2.case8;

public final class Case8Utils {
    public static void check(boolean ok) {
        if (!ok) throw new AssertionError();
    }
    public static void reject(Runnable r) {
        try {
            r.run();
        } catch (IllegalStateException expected) {
            return;
        }
        throw new AssertionError("Expected builder failure");
    }
}
