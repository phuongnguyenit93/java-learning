package com.example.learning.casestudy.domain.group2.case6;

public record Config(String endpoint) {
    public Config {
        if (endpoint == null || endpoint.isBlank())
            throw new IllegalArgumentException("endpoint");
    }
}
