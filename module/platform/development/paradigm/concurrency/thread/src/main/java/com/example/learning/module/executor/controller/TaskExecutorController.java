package com.example.learning.module.executor.controller;

import com.example.learning.module.executor.context.DemoContext;
import com.example.learning.module.executor.config.AsyncExceptionProbe;
import com.example.learning.module.executor.service.TaskExecutorService;
import org.slf4j.MDC;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.UUID;

@RestController
@RequestMapping("/spring-executor")
public class TaskExecutorController {

    private final TaskExecutorService taskExecutorService;
    private final AsyncExceptionProbe asyncExceptionProbe;

    public TaskExecutorController(
            TaskExecutorService taskExecutorService,
            AsyncExceptionProbe asyncExceptionProbe
    ) {
        this.taskExecutorService = taskExecutorService;
        this.asyncExceptionProbe = asyncExceptionProbe;
    }

    /**
     * README: readme/vi/menu/9.Spring_Task_Executor/SpringExecutor.md#spring-async-demo
     * Purpose: Chứng minh @Async dispatch sang custom ThreadPoolTaskExecutor và TaskDecorator propagate context.
     */
    @GetMapping("/async-context")
    public CompletableFuture<Map<String, Object>> asyncWithContext() {
        String callerThread = Thread.currentThread().getName();
        String previousDemoContext = DemoContext.get();
        String previousRequestId = MDC.get("requestId");
        DemoContext.set("REQUEST-123");
        MDC.put("requestId", "REQUEST-123");
        try {
            return taskExecutorService.runAsync(callerThread);
        } finally {
            if (previousDemoContext == null) {
                DemoContext.remove();
            } else {
                DemoContext.set(previousDemoContext);
            }

            if (previousRequestId == null) {
                MDC.remove("requestId");
            } else {
                MDC.put("requestId", previousRequestId);
            }
        }
    }

    /**
     * README: readme/vi/menu/9.Spring_Task_Executor/SpringExecutor.md#async-return-type
     * Purpose: Chứng minh failure trong @Async void đi qua AsyncUncaughtExceptionHandler, còn submission rejection là failure boundary riêng.
     */
    @GetMapping("/void-exception")
    public CompletableFuture<Map<String, Object>> asyncVoidException() {
        String correlationId = UUID.randomUUID().toString();
        CompletableFuture<Map<String, Object>> observed = asyncExceptionProbe
                .register(correlationId)
                .orTimeout(2, TimeUnit.SECONDS);

        try {
            taskExecutorService.failWithoutFuture(correlationId);
        } catch (RuntimeException submissionFailure) {
            // Rejection có thể xảy ra đồng bộ trước khi @Async method thật sự chạy.
            // Complete observation future để registration luôn có terminal state và cleanup được kích hoạt.
            asyncExceptionProbe.completeExceptionally(correlationId, submissionFailure);
        }

        return observed;
    }
}
