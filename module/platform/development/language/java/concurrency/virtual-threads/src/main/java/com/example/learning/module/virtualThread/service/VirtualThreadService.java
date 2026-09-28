package com.example.learning.module.virtualThread.service;

import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class VirtualThreadService {

    public Map<String, Object> basicDemo() throws InterruptedException {
        AtomicBoolean virtual = new AtomicBoolean(false);
        AtomicReference<String> description = new AtomicReference<>();

        Thread worker = Thread.ofVirtual()
                .name("virtual-basic")
                .start(() -> {
                    virtual.set(Thread.currentThread().isVirtual());
                    description.set(Thread.currentThread().toString());
                });

        try {
            joinOrFail(worker);
        } finally {
            cleanupThreads(worker);
        }
        return map(
                "isVirtual", virtual.get(),
                "daemon", worker.isDaemon(),
                "priority", worker.getPriority(),
                "normalPriority", Thread.NORM_PRIORITY,
                "thread", description.get(),
                "stateAfterJoin", worker.getState().name()
        );
    }

    public Map<String, Object> creationApisDemo() throws InterruptedException {
        AtomicBoolean startVirtualThreadIsVirtual = new AtomicBoolean(false);
        AtomicBoolean builderIsVirtual = new AtomicBoolean(false);
        AtomicReference<String> builderName = new AtomicReference<>();

        Thread direct = Thread.startVirtualThread(
                () -> startVirtualThreadIsVirtual.set(Thread.currentThread().isVirtual())
        );
        Thread builder = Thread.ofVirtual()
                .name("virtual-builder")
                .start(() -> {
                    builderIsVirtual.set(Thread.currentThread().isVirtual());
                    builderName.set(Thread.currentThread().getName());
                });

        try {
            joinOrFail(direct);
            joinOrFail(builder);
        } finally {
            cleanupThreads(direct, builder);
        }

        return map(
                "startVirtualThreadIsVirtual", startVirtualThreadIsVirtual.get(),
                "builderIsVirtual", builderIsVirtual.get(),
                "builderName", builderName.get(),
                "bothTerminated", !direct.isAlive() && !builder.isAlive()
        );
    }

    public Map<String, Object> blockingScaleDemo() throws Exception {
        int tasks = 100;
        Duration wait = Duration.ofMillis(20);

        long platformStart = System.nanoTime();
        try (var platformExecutor = Executors.newFixedThreadPool(10)) {
            for (int i = 0; i < tasks; i++) {
                platformExecutor.submit(() -> {
                    Thread.sleep(wait);
                    return null;
                });
            }
        }
        long platformMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - platformStart);

        long virtualStart = System.nanoTime();
        try (var virtualExecutor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < tasks; i++) {
                virtualExecutor.submit(() -> {
                    Thread.sleep(wait);
                    return null;
                });
            }
        }
        long virtualMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - virtualStart);

        return map(
                "tasks", tasks,
                "simulatedBlockingMillisPerTask", wait.toMillis(),
                "platformPoolSize", 10,
                "platformElapsedMillis", platformMillis,
                "virtualElapsedMillis", virtualMillis,
                "benchmarkGrade", false
        );
    }

    public Map<String, Object> limitedResourceDemo() throws Exception {
        int tasks = 30;
        int permits = 5;
        Semaphore semaphore = new Semaphore(permits);
        AtomicInteger active = new AtomicInteger();
        AtomicInteger maxActive = new AtomicInteger();
        CountDownLatch done = new CountDownLatch(tasks);

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < tasks; i++) {
                executor.submit(() -> {
                    boolean acquired = false;
                    try {
                        semaphore.acquire();
                        acquired = true;
                        int current = active.incrementAndGet();
                        maxActive.accumulateAndGet(current, Math::max);
                        Thread.sleep(10);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        if (acquired) {
                            active.decrementAndGet();
                            semaphore.release();
                        }
                        done.countDown();
                    }
                });
            }

            if (!done.await(2, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Virtual-thread demo không hoàn thành đúng hạn");
            }
        }

        return map(
                "tasks", tasks,
                "permits", permits,
                "maxConcurrentInsideResource", maxActive.get(),
                "availablePermitsAtEnd", semaphore.availablePermits(),
                "limitRespected", maxActive.get() <= permits
        );
    }

    public Map<String, Object> threadLocalDemo() throws InterruptedException {
        ThreadLocal<String> context = new ThreadLocal<>();
        InheritableThreadLocal<String> inheritableContext = new InheritableThreadLocal<>();
        AtomicReference<String> firstObserved = new AtomicReference<>();
        AtomicReference<String> secondObserved = new AtomicReference<>();
        AtomicReference<String> inheritedByDefault = new AtomicReference<>();
        AtomicReference<String> inheritedWhenDisabled = new AtomicReference<>();
        AtomicBoolean cleanedInsideFirstThread = new AtomicBoolean(false);

        Thread first = Thread.startVirtualThread(() -> {
            context.set("request-A");
            try {
                firstObserved.set(context.get());
            } finally {
                context.remove();
                cleanedInsideFirstThread.set(context.get() == null);
            }
        });

        Thread second = Thread.startVirtualThread(() -> secondObserved.set(context.get()));

        try {
            joinOrFail(first);
            joinOrFail(second);
        } finally {
            cleanupThreads(first, second);
        }

        inheritableContext.set("parent-context");
        Thread defaultInheritance = null;
        Thread disabledInheritance = null;
        try {
            defaultInheritance = Thread.ofVirtual()
                    .name("virtual-inherit-default")
                    .start(() -> inheritedByDefault.set(inheritableContext.get()));
            disabledInheritance = Thread.ofVirtual()
                    .name("virtual-inherit-disabled")
                    .inheritInheritableThreadLocals(false)
                    .start(() -> inheritedWhenDisabled.set(inheritableContext.get()));

            joinOrFail(defaultInheritance);
            joinOrFail(disabledInheritance);
        } finally {
            inheritableContext.remove();
            cleanupThreads(defaultInheritance, disabledInheritance);
        }

        return map(
                "firstThreadContext", firstObserved.get(),
                "firstThreadCleaned", cleanedInsideFirstThread.get(),
                "secondThreadInheritedPlainThreadLocal", secondObserved.get(),
                "plainThreadLocalIsPerThread", secondObserved.get() == null,
                "inheritableThreadLocalDefault", inheritedByDefault.get(),
                "inheritableThreadLocalWhenDisabled", inheritedWhenDisabled.get(),
                "virtualBuilderCanDisableInheritance", inheritedWhenDisabled.get() == null
        );
    }

    private static Map<String, Object> map(Object... pairs) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            result.put((String) pairs[i], pairs[i + 1]);
        }
        return result;
    }

    private static void joinOrFail(Thread worker) throws InterruptedException {
        worker.join(2_000);
        if (worker.isAlive()) {
            worker.interrupt();
            worker.join(2_000);
        }
        if (worker.isAlive()) {
            throw new IllegalStateException("Virtual thread không terminate đúng hạn: " + worker.getName());
        }
    }

    private static void cleanupThreads(Thread... workers) throws InterruptedException {
        for (Thread worker : workers) {
            if (worker != null && worker.isAlive()) {
                worker.interrupt();
            }
        }

        for (Thread worker : workers) {
            if (worker == null || !worker.isAlive()) {
                continue;
            }
            worker.join(2_000);
            if (worker.isAlive()) {
                throw new IllegalStateException("Virtual thread vẫn còn sống sau cleanup: " + worker.getName());
            }
        }
    }
}
