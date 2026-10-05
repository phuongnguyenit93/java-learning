package com.example.learning.module.web.cache.controller;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.context.request.ServletWebRequest;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/spring-web/cache")
public class ConditionalCacheExperimentController {

    private static final String ETAG = "\"spring-web-cache-v1\"";
    private static final long LAST_MODIFIED =
            Instant.parse("2026-01-01T00:00:00Z").toEpochMilli();

    /**
     * README: readme/en/menu/11.WebSupportResourceDelivery/WebSupportResourceDelivery.md#conditional-requests-and-http-caching
     * Purpose: Observe a deterministic 200 response become 304 when the client sends the current validator.
     */
    @GetMapping("/conditional")
    public ResponseEntity<Map<String, Object>> conditional() {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
        ServletWebRequest webRequest =
                new ServletWebRequest(attributes.getRequest(), attributes.getResponse());

        if (webRequest.checkNotModified(ETAG, LAST_MODIFIED)) {
            return null;
        }

        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(60, TimeUnit.SECONDS).cachePublic())
                .eTag(ETAG)
                .lastModified(LAST_MODIFIED)
                .body(Map.of(
                        "resource", "spring-web-cache-demo",
                        "version", 1,
                        "etag", ETAG,
                        "lastModifiedEpochMillis", LAST_MODIFIED
                ));
    }
}
