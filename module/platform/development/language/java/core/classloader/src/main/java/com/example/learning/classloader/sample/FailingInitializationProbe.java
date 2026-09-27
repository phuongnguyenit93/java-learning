package com.example.learning.classloader.sample;

public final class FailingInitializationProbe {

    static final String VALUE = fail();

    private FailingInitializationProbe() {
    }

    private static String fail() {
        throw new IllegalStateException("controlled initialization failure");
    }
}
