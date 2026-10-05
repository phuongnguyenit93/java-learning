package com.example.learning.module.executor.controller;

import com.example.learning.module.executor.context.DemoContext;
import com.example.learning.module.executor.config.AsyncExceptionProbe;
import com.example.learning.module.executor.service.TaskExecutorService;
import com.example.learning.module.experiment.service.ConcurrencyExperimentService;
import org.slf4j.MDC;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.UUID;

@RestController
@RequestMapping("/spring-executor")
public class TaskExecutorController {

    private final TaskExecutorService taskExecutorService;
    private final AsyncExceptionProbe asyncExceptionProbe;
    private final ConcurrencyExperimentService concurrencyExperimentService;

    public TaskExecutorController(
            TaskExecutorService taskExecutorService,
            AsyncExceptionProbe asyncExceptionProbe,
            ConcurrencyExperimentService concurrencyExperimentService
    ) {
        this.taskExecutorService = taskExecutorService;
        this.asyncExceptionProbe = asyncExceptionProbe;
        this.concurrencyExperimentService = concurrencyExperimentService;
    }

    /**
     * README: readme/vi/menu/3.ContextPropagation/ContextPropagation.md#spring-async-demo
     * Purpose: Chứng minh @Async dispatch sang custom ThreadPoolTaskExecutor và TaskDecorator propagate context.
    */
    @GetMapping("/async-context")
    public Map<String, Object> asyncWithContext() {
        Thread caller = Thread.currentThread();
        String callerThread = caller.getName();
        long callerThreadId = caller.threadId();
        String previousDemoContext = DemoContext.get();
        String previousRequestId = MDC.get("requestId");
        DemoContext.set("REQUEST-123");
        MDC.put("requestId", "REQUEST-123");
        CompletableFuture<Map<String, Object>> evidence;
        try {
            evidence = taskExecutorService.runAsync(callerThread, callerThreadId);
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
        return awaitEvidence(evidence, "Async context experiment did not finish");
    }

    /**
     * README: readme/vi/menu/2.Spring_Task_Executor/SpringExecutor.md#async-return-type
     * Purpose: Chứng minh failure trong @Async void đi qua AsyncUncaughtExceptionHandler.
     */
    @GetMapping("/void-exception")
    public Map<String, Object> asyncVoidException() {
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

        return awaitEvidence(observed, "Async void failure was not observed");
    }

    /** README: readme/vi/menu/2.Spring_Task_Executor/SpringExecutor.md#spring-executor-config */
    @GetMapping("/saturation")
    public Map<String, Object> saturation() {
        return concurrencyExperimentService.executorSaturationDemo();
    }

    private static Map<String, Object> awaitEvidence(
            CompletableFuture<Map<String, Object>> future,
            String timeoutMessage
    ) {
        try {
            return future.get(3, TimeUnit.SECONDS);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(timeoutMessage, error);
        } catch (TimeoutException error) {
            throw new IllegalStateException(timeoutMessage, error);
        } catch (ExecutionException error) {
            Throwable cause = error.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException("Async experiment failed", cause);
        }
    }
}
