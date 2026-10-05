package com.example.learning.module.context.controller;

import com.example.learning.module.context.BlockingExperiment;
import com.example.learning.module.context.filter.CorrelationContextFilter;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;

@RestController
@RequestMapping("/api/reactive/context")
public class ReactiveContextController {

    /**
     * README: readme/en/menu/6.CrossCuttingProcessing/CrossCuttingProcessing.md#webflux-reactor-context
     * Purpose: Prove that a WebFilter can attach contextual data to the reactive subscription.
     */
    @GetMapping("/reactor")
    public Mono<Map<String, Object>> reactorContext(
            @RequestHeader(
                    value = CorrelationContextFilter.CORRELATION_HEADER,
                    defaultValue = "demo-context"
            ) String correlationId
    ) {
        String effectiveCorrelationId =
                CorrelationContextFilter.normalizeCorrelationId(correlationId);
        String entryThread = Thread.currentThread().getName();

        return Mono.delay(Duration.ofMillis(20))
                .then(Mono.deferContextual(contextView -> {
                    String reactorCorrelationId =
                            contextView.get(CorrelationContextFilter.REACTOR_CONTEXT_KEY);
                    String resumedThread = Thread.currentThread().getName();

                    Map<String, Object> evidence = new LinkedHashMap<>();
                    evidence.put("requestCorrelationId", effectiveCorrelationId);
                    evidence.put("reactorCorrelationId", reactorCorrelationId);
                    evidence.put(
                            "sameValue",
                            reactorCorrelationId.equals(effectiveCorrelationId)
                    );
                    evidence.put("entryThread", entryThread);
                    evidence.put("resumedThread", resumedThread);
                    evidence.put(
                            "threadChangedAcrossAsyncBoundary",
                            !entryThread.equals(resumedThread)
                    );
                    return Mono.just(evidence);
                }));
    }

    /**
     * README: readme/en/menu/6.CrossCuttingProcessing/CrossCuttingProcessing.md#webflux-blocking-execution
     * Purpose: Prove that Spring 6.1 can invoke selected annotated controller methods on a bounded executor.
     */
    @BlockingExperiment
    @GetMapping("/blocking-execution")
    public Map<String, Object> blockingExecution() {
        long startedAt = System.nanoTime();
        try {
            Thread.sleep(40);
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(
                    SERVICE_UNAVAILABLE,
                    "bounded blocking experiment was interrupted",
                    interruptedException
            );
        }

        String threadName = Thread.currentThread().getName();
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("thread", threadName);
        evidence.put(
                "offloadedToConfiguredExecutor",
                threadName.startsWith("webflux-blocking-")
        );
        evidence.put(
                "elapsedMillis",
                (System.nanoTime() - startedAt) / 1_000_000
        );
        evidence.put("boundedSleepMillis", 40);
        return evidence;
    }
}
