package com.example.learning.config;


import com.example.learning.module.feign.exception.FeignClientException;
import feign.FeignException;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.net.ConnectException;
import java.time.Duration;

@Configuration
public class CircuitBreakerPlatformConfig {
    @Bean
    public CircuitBreakerRegistry circuitBreakerRegistry() {
        // 1. Khai báo quy tắc (Config)
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .failureRateThreshold(30)
                .waitDurationInOpenState(Duration.ofSeconds(10))
                .slidingWindowSize(10)
                .minimumNumberOfCalls(5)
                .recordExceptions(IOException.class, ConnectException.class, FeignException.class)
                .build();

        // 2. Tạo Registry
        CircuitBreakerRegistry registry = CircuitBreakerRegistry.of(config);

        // 3. Tạo instance cụ thể
        registry.circuitBreaker("customCircuitConfig", config);

        return registry;
    }

    @Bean
    public RetryRegistry retryRegistry() {
        RetryConfig config = RetryConfig.custom()
                .maxAttempts(5)
                .waitDuration(Duration.ofMillis(2000))
                .retryExceptions(IOException.class, ConnectException.class,FeignException.class)
                .build();

        // Tạo Registry
        RetryRegistry registry = RetryRegistry.ofDefaults();

        // QUAN TRỌNG: Phải dùng phương thức retry(name, config) để tạo hẳn 1 thực thể
        registry.retry("customRetryConfig", config);

        return registry;
    }

    @Bean
    public TimeLimiterRegistry timeLimiterRegistry() {
        TimeLimiterConfig config = TimeLimiterConfig.custom()
                .timeoutDuration(Duration.ofSeconds(2))
                .cancelRunningFuture(true)
                .build();

        TimeLimiterRegistry registry = TimeLimiterRegistry.of(config);
        registry.timeLimiter("customTimeLimiterConfig");

        return registry;
    }
}
