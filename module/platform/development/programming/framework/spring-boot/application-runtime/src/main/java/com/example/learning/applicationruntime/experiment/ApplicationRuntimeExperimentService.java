package com.example.learning.applicationruntime.experiment;

import com.example.learning.applicationruntime.lifecycle.RuntimeLifecycleProbe;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.availability.ApplicationAvailability;
import org.springframework.core.env.Environment;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Service
public class ApplicationRuntimeExperimentService {

    private final RuntimeLifecycleProbe lifecycleProbe;
    private final ApplicationArguments applicationArguments;
    private final ApplicationAvailability availability;
    private final AsyncTaskExecutor applicationTaskExecutor;
    private final Environment environment;

    public ApplicationRuntimeExperimentService(
            RuntimeLifecycleProbe lifecycleProbe,
            ApplicationArguments applicationArguments,
            ApplicationAvailability availability,
            @Qualifier("applicationTaskExecutor") AsyncTaskExecutor applicationTaskExecutor,
            Environment environment
    ) {
        this.lifecycleProbe = lifecycleProbe;
        this.applicationArguments = applicationArguments;
        this.availability = availability;
        this.applicationTaskExecutor = applicationTaskExecutor;
        this.environment = environment;
    }

    public Map<String, Object> lifecycleTimeline() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("observations", lifecycleProbe.snapshot());
        result.put("currentLiveness", availability.getLivenessState().toString());
        result.put("currentReadiness", availability.getReadinessState().toString());
        return result;
    }

    public Map<String, Object> applicationArguments() {
        Map<String, List<String>> optionValues = new LinkedHashMap<>();
        applicationArguments.getOptionNames()
                .stream()
                .sorted()
                .forEach(name -> optionValues.put(name, applicationArguments.getOptionValues(name)));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sourceArgs", List.of(applicationArguments.getSourceArgs()));
        result.put("optionNames", applicationArguments.getOptionNames().stream().sorted().toList());
        result.put("optionValues", optionValues);
        result.put("nonOptionArgs", applicationArguments.getNonOptionArgs());
        return result;
    }

    public Map<String, Object> taskExecutor() {
        Thread caller = Thread.currentThread();
        CompletableFuture<Map<String, Object>> workerEvidence = new CompletableFuture<>();

        applicationTaskExecutor.execute(() -> {
            try {
                Thread worker = Thread.currentThread();
                Map<String, Object> evidence = new LinkedHashMap<>();
                evidence.put("workerThread", worker.getName());
                evidence.put("workerThreadId", worker.threadId());
                evidence.put("workerVirtual", worker.isVirtual());
                workerEvidence.complete(evidence);
            } catch (Throwable error) {
                workerEvidence.completeExceptionally(error);
            }
        });

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("executorType", applicationTaskExecutor.getClass().getName());
        result.put("virtualThreadsEnabled",
                environment.getProperty("spring.threads.virtual.enabled", Boolean.class, false));
        result.put("callerThread", caller.getName());
        result.put("callerThreadId", caller.threadId());

        Map<String, Object> worker = await(workerEvidence);
        result.putAll(worker);
        result.put("differentThread", caller.threadId() != (long) worker.get("workerThreadId"));
        return result;
    }

    private static Map<String, Object> await(CompletableFuture<Map<String, Object>> future) {
        try {
            return future.get(3, TimeUnit.SECONDS);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Task executor experiment was interrupted", error);
        } catch (ExecutionException | TimeoutException error) {
            throw new IllegalStateException("Task executor experiment did not complete", error);
        }
    }
}
