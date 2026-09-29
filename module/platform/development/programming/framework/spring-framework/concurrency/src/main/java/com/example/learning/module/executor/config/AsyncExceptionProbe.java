package com.example.learning.module.executor.config;

import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class AsyncExceptionProbe implements AsyncUncaughtExceptionHandler {

    private final ConcurrentMap<String, CompletableFuture<Map<String, Object>>> pending =
            new ConcurrentHashMap<>();

    public CompletableFuture<Map<String, Object>> register(String correlationId) {
        CompletableFuture<Map<String, Object>> future = new CompletableFuture<>();
        pending.put(correlationId, future);
        future.whenComplete((result, error) -> pending.remove(correlationId, future));
        return future;
    }

    public void completeExceptionally(String correlationId, Throwable error) {
        CompletableFuture<Map<String, Object>> future = pending.remove(correlationId);
        if (future != null) {
            future.completeExceptionally(error);
        }
    }

    @Override
    public void handleUncaughtException(Throwable ex, Method method, Object... params) {
        String correlationId = params.length > 0 ? String.valueOf(params[0]) : "unknown";
        CompletableFuture<Map<String, Object>> future = pending.remove(correlationId);
        if (future == null) {
            return;
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("handlerInvoked", true);
        result.put("method", method.getName());
        result.put("exceptionType", ex.getClass().getSimpleName());
        result.put("message", ex.getMessage());
        result.put("correlationId", correlationId);
        result.put("handlerThread", Thread.currentThread().getName());
        future.complete(result);
    }
}
