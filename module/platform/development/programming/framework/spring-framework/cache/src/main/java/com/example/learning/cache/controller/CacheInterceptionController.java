package com.example.learning.cache.controller;

import com.example.learning.cache.service.CacheExperimentService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/spring-cache/interception")
public class CacheInterceptionController {

    private final CacheExperimentService cacheExperimentService;

    public CacheInterceptionController(CacheExperimentService cacheExperimentService) {
        this.cacheExperimentService = cacheExperimentService;
    }

    /**
     * README: readme/en/menu/6.InterceptionBoundary/InterceptionBoundary.md#self-invocation
     * Purpose: Compare calls through the Spring proxy with self-invocation inside the target bean.
     */
    @GetMapping("/self-invocation")
    public Map<String, Object> selfInvocation() {
        return cacheExperimentService.selfInvocationExperiment();
    }
}
