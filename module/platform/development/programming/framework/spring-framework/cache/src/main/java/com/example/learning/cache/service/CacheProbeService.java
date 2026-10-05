package com.example.learning.cache.service;

import com.example.learning.cache.model.CacheValue;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class CacheProbeService {

    private final ConcurrentMap<String, AtomicInteger> loadCounts = new ConcurrentHashMap<>();

    @Cacheable(cacheNames = "cache-key-incomplete-demo", key = "#p0 + ':' + #p1")
    public CacheValue loadWithIncompleteKey(String experimentId, String businessKey, boolean includeReviews) {
        return newValue(experimentId, businessKey, includeReviews);
    }

    @Cacheable(cacheNames = "cache-key-complete-demo", key = "#p0 + ':' + #p1 + ':' + #p2")
    public CacheValue loadWithCompleteKey(String experimentId, String businessKey, boolean includeReviews) {
        return newValue(experimentId, businessKey, includeReviews);
    }

    @Cacheable(cacheNames = "cache-self-invocation-demo", key = "#p0 + ':' + #p1")
    public CacheValue loadForSelfInvocation(String experimentId, String businessKey) {
        return newValue(experimentId, businessKey, false);
    }

    public CacheValue[] invokeCachedMethodTwiceInternally(String experimentId, String businessKey) {
        CacheValue first = this.loadForSelfInvocation(experimentId, businessKey);
        CacheValue second = this.loadForSelfInvocation(experimentId, businessKey);
        return new CacheValue[]{first, second};
    }

    @Cacheable(cacheNames = "cache-sync-demo", key = "#p0 + ':' + #p1", sync = true)
    public CacheValue loadSynchronized(String experimentId, String businessKey) {
        sleepBriefly();
        return newValue(experimentId, businessKey, false);
    }

    public int loadCount(String experimentId) {
        AtomicInteger counter = loadCounts.get(experimentId);
        return counter == null ? 0 : counter.get();
    }

    public void clearObservation(String experimentId) {
        loadCounts.remove(experimentId);
    }

    private CacheValue newValue(String experimentId, String businessKey, boolean includeReviews) {
        int loadSequence = loadCounts
                .computeIfAbsent(experimentId, ignored -> new AtomicInteger())
                .incrementAndGet();
        return new CacheValue(
                businessKey,
                includeReviews,
                loadSequence,
                Thread.currentThread().getName()
        );
    }

    private void sleepBriefly() {
        try {
            Thread.sleep(150);
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Synchronized cache experiment was interrupted", interruptedException);
        }
    }
}
