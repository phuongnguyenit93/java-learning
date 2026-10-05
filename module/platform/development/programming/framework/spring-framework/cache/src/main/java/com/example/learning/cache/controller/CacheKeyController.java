package com.example.learning.cache.controller;

import com.example.learning.cache.service.CacheExperimentService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/spring-cache/key")
public class CacheKeyController {

    private final CacheExperimentService cacheExperimentService;

    public CacheKeyController(CacheExperimentService cacheExperimentService) {
        this.cacheExperimentService = cacheExperimentService;
    }

    /**
     * README: readme/en/menu/4.KeyAndResolution/KeyAndResolution.md#cache-key-design-pitfalls
     * Purpose: Observe an incomplete key reusing the wrong cached variant, then compare it with a complete key.
     */
    @GetMapping("/hit-miss")
    public Map<String, Object> keyAndHitMiss() {
        return cacheExperimentService.keyAndHitMissExperiment();
    }
}
