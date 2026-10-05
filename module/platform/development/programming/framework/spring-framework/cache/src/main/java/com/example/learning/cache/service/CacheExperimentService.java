package com.example.learning.cache.service;

import com.example.learning.cache.model.CacheValue;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Service
public class CacheExperimentService {

    private static final int CONCURRENT_CALLERS = 4;
    private static final int READY_TIMEOUT_SECONDS = 3;
    private static final int RESULT_TIMEOUT_SECONDS = 5;
    private static final int TERMINATION_TIMEOUT_SECONDS = 2;

    private final CacheProbeService cacheProbeService;
    private final CacheManager cacheManager;

    public CacheExperimentService(CacheProbeService cacheProbeService, CacheManager cacheManager) {
        this.cacheProbeService = cacheProbeService;
        this.cacheManager = cacheManager;
    }

    public Map<String, Object> keyAndHitMissExperiment() {
        String incompleteExperimentId = UUID.randomUUID().toString();
        String completeExperimentId = UUID.randomUUID().toString();
        String businessKey = "book-42";
        String incompleteKey = cacheKey(incompleteExperimentId, businessKey);
        String completePlainKey = cacheKey(completeExperimentId, businessKey, false);
        String completeReviewsKey = cacheKey(completeExperimentId, businessKey, true);

        try {
            CacheValue incompletePlain = cacheProbeService.loadWithIncompleteKey(
                    incompleteExperimentId,
                    businessKey,
                    false
            );
            CacheValue incompleteReviewsRequest = cacheProbeService.loadWithIncompleteKey(
                    incompleteExperimentId,
                    businessKey,
                    true
            );

            CacheValue completePlain = cacheProbeService.loadWithCompleteKey(
                    completeExperimentId,
                    businessKey,
                    false
            );
            CacheValue completeReviews = cacheProbeService.loadWithCompleteKey(
                    completeExperimentId,
                    businessKey,
                    true
            );

            Map<String, Object> incomplete = new LinkedHashMap<>();
            incomplete.put("firstRequestIncludeReviews", false);
            incomplete.put("firstReturned", incompletePlain);
            incomplete.put("secondRequestIncludeReviews", true);
            incomplete.put("secondReturned", incompleteReviewsRequest);
            incomplete.put("loaderInvocations", cacheProbeService.loadCount(incompleteExperimentId));
            incomplete.put(
                    "wrongVariantWasReused",
                    !incompleteReviewsRequest.includeReviews()
                            && incompleteReviewsRequest.loadSequence() == incompletePlain.loadSequence()
            );

            Map<String, Object> complete = new LinkedHashMap<>();
            complete.put("firstRequestIncludeReviews", false);
            complete.put("firstReturned", completePlain);
            complete.put("secondRequestIncludeReviews", true);
            complete.put("secondReturned", completeReviews);
            complete.put("loaderInvocations", cacheProbeService.loadCount(completeExperimentId));
            complete.put(
                    "resultShapingInputCreatedDistinctEntry",
                    completeReviews.includeReviews()
                            && completeReviews.loadSequence() != completePlain.loadSequence()
            );

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("incompleteKey", incomplete);
            result.put("completeKey", complete);
            result.put(
                    "keyCompletenessChangedCorrectness",
                    Boolean.TRUE.equals(incomplete.get("wrongVariantWasReused"))
                            && Boolean.TRUE.equals(complete.get("resultShapingInputCreatedDistinctEntry"))
            );
            return result;
        } finally {
            evict("cache-key-incomplete-demo", incompleteKey);
            evict("cache-key-complete-demo", completePlainKey);
            evict("cache-key-complete-demo", completeReviewsKey);
            cacheProbeService.clearObservation(incompleteExperimentId);
            cacheProbeService.clearObservation(completeExperimentId);
        }
    }

    public Map<String, Object> selfInvocationExperiment() {
        String proxiedExperimentId = UUID.randomUUID().toString();
        String selfExperimentId = UUID.randomUUID().toString();
        String businessKey = "book-42";

        try {
            CacheValue proxiedFirst = cacheProbeService.loadForSelfInvocation(proxiedExperimentId, businessKey);
            CacheValue proxiedSecond = cacheProbeService.loadForSelfInvocation(proxiedExperimentId, businessKey);
            CacheValue[] internal = cacheProbeService.invokeCachedMethodTwiceInternally(
                    selfExperimentId,
                    businessKey
            );

            Map<String, Object> proxied = new LinkedHashMap<>();
            proxied.put("first", proxiedFirst);
            proxied.put("second", proxiedSecond);
            proxied.put("loaderInvocations", cacheProbeService.loadCount(proxiedExperimentId));
            proxied.put("cacheAdviceApplied", proxiedFirst.loadSequence() == proxiedSecond.loadSequence());

            Map<String, Object> selfInvocation = new LinkedHashMap<>();
            selfInvocation.put("first", internal[0]);
            selfInvocation.put("second", internal[1]);
            selfInvocation.put("loaderInvocations", cacheProbeService.loadCount(selfExperimentId));
            selfInvocation.put("cacheAdviceApplied", internal[0].loadSequence() == internal[1].loadSequence());

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("proxiedCalls", proxied);
            result.put("selfInvocation", selfInvocation);
            result.put(
                    "selfInvocationBypassedCacheAdvice",
                    cacheProbeService.loadCount(selfExperimentId) == 2
                            && cacheProbeService.loadCount(proxiedExperimentId) == 1
            );
            return result;
        } finally {
            evict("cache-self-invocation-demo", cacheKey(proxiedExperimentId, businessKey));
            evict("cache-self-invocation-demo", cacheKey(selfExperimentId, businessKey));
            cacheProbeService.clearObservation(proxiedExperimentId);
            cacheProbeService.clearObservation(selfExperimentId);
        }
    }

    public Map<String, Object> synchronizedLoadingExperiment() {
        String experimentId = UUID.randomUUID().toString();
        String businessKey = "book-42";
        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_CALLERS);
        CountDownLatch ready = new CountDownLatch(CONCURRENT_CALLERS);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<CacheValue>> futures = new ArrayList<>();

        try {
            for (int index = 0; index < CONCURRENT_CALLERS; index++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    if (!start.await(READY_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Concurrent callers did not start together");
                    }
                    return cacheProbeService.loadSynchronized(experimentId, businessKey);
                }));
            }

            if (!ready.await(READY_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Concurrent callers were not ready in time");
            }
            start.countDown();

            List<CacheValue> values = new ArrayList<>();
            for (Future<CacheValue> future : futures) {
                values.add(future.get(RESULT_TIMEOUT_SECONDS, TimeUnit.SECONDS));
            }

            Set<Integer> loadSequences = new LinkedHashSet<>();
            Set<String> loaderThreads = new LinkedHashSet<>();
            for (CacheValue value : values) {
                loadSequences.add(value.loadSequence());
                loaderThreads.add(value.loaderThread());
            }

            int loaderInvocations = cacheProbeService.loadCount(experimentId);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("experimentId", experimentId);
            result.put("concurrentCallers", CONCURRENT_CALLERS);
            result.put("loaderInvocations", loaderInvocations);
            result.put("distinctReturnedLoadSequences", loadSequences.size());
            result.put("loaderThreadsObservedInReturnedValue", loaderThreads);
            result.put("cacheImplementation", cacheImplementation("cache-sync-demo"));
            result.put("allCallersObservedOneCachedLoad", loaderInvocations == 1 && loadSequences.size() == 1);
            return result;
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Concurrent cache experiment was interrupted", interruptedException);
        } catch (ExecutionException | TimeoutException executionFailure) {
            throw new IllegalStateException("Concurrent cache experiment failed", executionFailure);
        } finally {
            start.countDown();
            for (Future<CacheValue> future : futures) {
                if (!future.isDone()) {
                    future.cancel(true);
                }
            }
            executor.shutdownNow();
            try {
                awaitTermination(executor);
            } finally {
                evict("cache-sync-demo", cacheKey(experimentId, businessKey));
                cacheProbeService.clearObservation(experimentId);
            }
        }
    }

    private String cacheKey(String experimentId, String businessKey) {
        return experimentId + ":" + businessKey;
    }

    private String cacheKey(String experimentId, String businessKey, boolean includeReviews) {
        return experimentId + ":" + businessKey + ":" + includeReviews;
    }

    private void evict(String cacheName, Object key) {
        Cache cache = cacheManager.getCache(cacheName);
        if (cache != null) {
            cache.evict(key);
        }
    }

    private String cacheImplementation(String cacheName) {
        Cache cache = cacheManager.getCache(cacheName);
        return cache == null ? "unavailable" : cache.getClass().getName();
    }

    private void awaitTermination(ExecutorService executor) {
        try {
            if (!executor.awaitTermination(TERMINATION_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Experiment executor did not terminate");
            }
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while cleaning up experiment executor", interruptedException);
        }
    }
}
