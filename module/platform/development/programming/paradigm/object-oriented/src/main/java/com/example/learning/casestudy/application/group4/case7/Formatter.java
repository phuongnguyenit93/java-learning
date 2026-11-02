package com.example.learning.casestudy.application.group4.case7;

public final class Formatter {
    public String format(String value) {
        if (value == null || value.isBlank())
            throw new IllegalArgumentException("value");
        return "[" + value.trim() + "]";
    }
}
