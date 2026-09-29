package com.example.learning.module.executor.service;

import com.example.learning.module.executor.context.DemoContext;
import org.slf4j.MDC;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Service
public class TaskExecutorService {

    @Async("threadLearningTaskExecutor")
    public CompletableFuture<Map<String, Object>> runAsync(String callerThread) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("callerThread", callerThread);
        result.put("workerThread", Thread.currentThread().getName());
        result.put("workerContext", DemoContext.get());
        result.put("workerMdcRequestId", MDC.get("requestId"));
        result.put("workerHasRequestAttributes", RequestContextHolder.getRequestAttributes() != null);
        result.put("differentThread", !callerThread.equals(Thread.currentThread().getName()));
        return CompletableFuture.completedFuture(result);
    }

    @Async("threadLearningTaskExecutor")
    public void failWithoutFuture(String correlationId) {
        throw new IllegalStateException("async void failure: " + correlationId);
    }
}
