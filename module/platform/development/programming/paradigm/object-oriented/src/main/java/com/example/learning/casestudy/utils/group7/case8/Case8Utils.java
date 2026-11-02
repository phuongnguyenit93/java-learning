package com.example.learning.casestudy.utils.group7.case8;

public final class Case8Utils {
    private Case8Utils() {

    }

    public static void check(boolean ok){
        if (!ok)throw new AssertionError();

    }

    @SuppressWarnings("unchecked")
    public static <E extends Throwable> RuntimeException propagate(Throwable cause) throws E {
        throw (E) cause;

    }
}
