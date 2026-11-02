package com.example.learning.casestudy.utils.group5.case5;

public final class Case5Utils {
    @SuppressWarnings("unchecked")
    public static <E extends Throwable> RuntimeException rethrow(Throwable error) throws E {
        throw (E) error;
    }
}
