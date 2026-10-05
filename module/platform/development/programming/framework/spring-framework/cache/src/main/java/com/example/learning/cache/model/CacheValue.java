package com.example.learning.cache.model;

public record CacheValue(
        String businessKey,
        boolean includeReviews,
        int loadSequence,
        String loaderThread
) {
}
