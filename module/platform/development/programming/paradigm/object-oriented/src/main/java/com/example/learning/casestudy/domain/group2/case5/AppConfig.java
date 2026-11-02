package com.example.learning.casestudy.domain.group2.case5;

public record AppConfig(String url) {
    public AppConfig {
        if (url == null || url.isBlank())
            throw new IllegalArgumentException("app.url missing");
    }
}
