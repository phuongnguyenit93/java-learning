package com.example.learning.casestudy.utils.group13.case2;

public final class Case2Utils {
    private Case2Utils() {
    }

    public static void check(boolean ok) {
        if (!ok) throw new AssertionError();
    }

    @SuppressWarnings("unchecked")
    public static <E extends Throwable> RuntimeException propagate(Throwable cause) throws E {
        throw (E) cause;
    }
}
