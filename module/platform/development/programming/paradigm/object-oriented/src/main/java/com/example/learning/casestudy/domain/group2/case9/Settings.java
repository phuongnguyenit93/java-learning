package com.example.learning.casestudy.domain.group2.case9;

public record Settings(String theme) {
    public Settings {
        if (theme == null || theme.isBlank())
            throw new IllegalArgumentException("theme");
    }
}
