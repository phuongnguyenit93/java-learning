package com.example.learning.casestudy.utils.group13.case1;

public final class Case1Utils {
    private Case1Utils() {
    }

    public static void check(boolean ok) {
        if (!ok) throw new AssertionError();
    }

    @SuppressWarnings("unchecked")
    public static <E extends Throwable> RuntimeException propagate(Throwable cause) throws E {
        throw (E) cause;
    }
}
