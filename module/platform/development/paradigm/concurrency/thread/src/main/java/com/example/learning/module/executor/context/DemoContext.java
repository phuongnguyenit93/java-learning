package com.example.learning.module.executor.context;

public final class DemoContext {

    private static final ThreadLocal<String> VALUE = new ThreadLocal<>();

    private DemoContext() {
    }

    public static void set(String value) {
        VALUE.set(value);
    }

    public static String get() {
        return VALUE.get();
    }

    public static void remove() {
        VALUE.remove();
    }
}

