package com.example.learning.cache.controller;

import com.example.learning.cache.service.CacheExperimentService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/spring-cache/concurrency")
public class CacheConcurrencyController {

    private final CacheExperimentService cacheExperimentService;

    public CacheConcurrencyController(CacheExperimentService cacheExperimentService) {
        this.cacheExperimentService = cacheExperimentService;
    }

    /**
     * README: readme/en/menu/7.ConcurrencyAndAsync/ConcurrencyAndAsync.md#cacheable-sync
     * Purpose: Trigger concurrent misses and observe synchronized loading with the configured cache provider.
     */
    @GetMapping("/sync-loading")
    public Map<String, Object> synchronizedLoading() {
        return cacheExperimentService.synchronizedLoadingExperiment();
    }
}
