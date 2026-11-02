package com.example.learning.casestudy.domain.group2.case12;

public record CacheKey(String id) {
    public CacheKey {
        if (id == null || id.isBlank())
            throw new IllegalArgumentException("id");
    }
}
