package com.example.learning.casestudy.utils.group8.case10;

public final class Case10Utils {
    private Case10Utils() {
    }

    @SuppressWarnings("unchecked")
    public static <E extends Throwable> void rethrow(Throwable failure) throws E {
        throw (E) failure;
    }
}
