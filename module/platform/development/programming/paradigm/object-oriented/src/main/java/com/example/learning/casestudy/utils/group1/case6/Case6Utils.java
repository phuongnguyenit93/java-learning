package com.example.learning.casestudy.utils.group1.case6;

public final class Case6Utils {
    private Case6Utils() {}

    public static void check(boolean ok) { if (!ok) throw new AssertionError(); }

    public static void rejects(Runnable r) {
        try {
            r.run();
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new AssertionError("expected rejection");
    }
}
