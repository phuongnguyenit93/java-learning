package com.example.learning.casestudy.model.group2.case5;

public final class UnsafeConfig {
    public static final String URL;
    static {
        String value = System.getProperty("demo.case5.url");
        if (value == null || value.isBlank())
            throw new IllegalStateException("missing URL");
        URL = value;
    }
}
