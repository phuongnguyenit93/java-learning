package com.example.learning.casestudy.utils.group2.case1;

public final class Case1Utils {
    public static void check(boolean ok) {
        if (!ok) throw new AssertionError();
    }
    public static void reject(Runnable r) {
        try {
            r.run();
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new AssertionError("Expected rejection");
    }
}
