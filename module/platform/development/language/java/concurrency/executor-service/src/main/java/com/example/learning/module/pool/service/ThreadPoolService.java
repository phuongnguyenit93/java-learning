package com.example.learning.module.pool.service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class ThreadPoolService {

    public Map<String, Object> executorFlowDemo() throws InterruptedException {
        CountDownLatch releaseTasks = new CountDownLatch(1);
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                1,
                2,
                10,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(1),
                namedFactory("flow-worker-"),
                new ThreadPoolExecutor.AbortPolicy()
        );

        List<Map<String, Object>> snapshots = new ArrayList<>();
        AtomicInteger completed = new AtomicInteger();
        Runnable blockingTask = () -> {
            await(releaseTasks);
            completed.incrementAndGet();
        };

        boolean fourthRejected = false;
        try {
            executor.execute(blockingTask);
            waitForPoolSize(executor, 1);
            snapshots.add(snapshot("afterTask1", executor));

            executor.execute(blockingTask);
            waitForQueueSize(executor, 1);
            snapshots.add(snapshot("afterTask2", executor));

            executor.execute(blockingTask);
            waitForPoolSize(executor, 2);
            snapshots.add(snapshot("afterTask3", executor));

            try {
                executor.execute(blockingTask);
            } catch (RejectedExecutionException expected) {
                fourthRejected = true;
            }

            return map(
                    "corePoolSize", executor.getCorePoolSize(),
                    "maximumPoolSize", executor.getMaximumPoolSize(),
                    "queueCapacity", 1,
                    "snapshots", snapshots,
                    "fourthTaskRejected", fourthRejected
            );
        } finally {
            releaseTasks.countDown();
            shutdownAndAwait(executor);
        }
    }

    public Map<String, Object> executeVsSubmitDemo() throws InterruptedException {
        AtomicReference<Throwable> executeUncaught = new AtomicReference<>();
        CountDownLatch executeExceptionObserved = new CountDownLatch(1);
        ThreadFactory executeFactory = runnable -> {
            Thread thread = new Thread(runnable, "execute-exception-worker");
            thread.setUncaughtExceptionHandler((ignored, error) -> {
                executeUncaught.set(error);
                executeExceptionObserved.countDown();
            });
            return thread;
        };

        ExecutorService executeExecutor = Executors.newSingleThreadExecutor(executeFactory);
        ExecutorService submitExecutor = Executors.newSingleThreadExecutor(namedFactory("submit-exception-worker-"));
        try {
            executeExecutor.execute(() -> {
                throw new IllegalStateException("execute failure");
            });
            if (!executeExceptionObserved.await(2, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Không quan sát được uncaught exception từ execute().");
            }

            Future<?> submitted = submitExecutor.submit(() -> {
                throw new IllegalArgumentException("submit failure");
            });

            boolean submitExceptionCapturedByFuture = false;
            String submitCause = null;
            try {
                submitted.get(2, TimeUnit.SECONDS);
            } catch (ExecutionException expected) {
                submitExceptionCapturedByFuture = true;
                submitCause = expected.getCause().getClass().getSimpleName();
            } catch (java.util.concurrent.TimeoutException e) {
                throw new IllegalStateException("submit() future không complete đúng hạn.", e);
            }

            return map(
                    "executeReturnsFuture", false,
                    "executeUncaughtExceptionObserved", executeUncaught.get() != null,
                    "executeExceptionType", executeUncaught.get() == null ? null : executeUncaught.get().getClass().getSimpleName(),
                    "submitReturnsFuture", true,
                    "submitExceptionCapturedByFuture", submitExceptionCapturedByFuture,
                    "submitExceptionCause", submitCause,
                    "submitFutureDone", submitted.isDone()
            );
        } finally {
            shutdownAndAwait(executeExecutor);
            shutdownAndAwait(submitExecutor);
        }
    }

    public Map<String, Object> fixedPoolReuseDemo() throws InterruptedException {
        Set<String> workerNames = ConcurrentHashMap.newKeySet();
        ExecutorService executor = Executors.newFixedThreadPool(2, namedFactory("fixed-worker-"));
        CountDownLatch completed = new CountDownLatch(6);

        try {
            for (int i = 0; i < 6; i++) {
                executor.execute(() -> {
                    workerNames.add(Thread.currentThread().getName());
                    completed.countDown();
                });
            }
            if (!completed.await(2, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Fixed pool không hoàn thành task đúng thời gian dự kiến.");
            }
        } finally {
            shutdownAndAwait(executor);
        }

        return map(
                "tasks", 6,
                "uniqueWorkers", workerNames.size(),
                "workerNames", workerNames
        );
    }

    public Map<String, Object> executorFactoriesDemo() throws InterruptedException {
        return map(
                "fixedThreadPool", fixedPoolFactorySample(),
                "cachedThreadPool", cachedPoolFactorySample(),
                "singleThreadExecutor", singleThreadFactorySample(),
                "scheduledThreadPool", scheduledPoolFactorySample()
        );
    }

    public Map<String, Object> scheduledExecutionModesDemo() throws InterruptedException {
        return map(
                "fixedRate", observePeriodicSchedule(true),
                "fixedDelay", observePeriodicSchedule(false)
        );
    }

    public Map<String, Object> scheduledFailureDemo() throws InterruptedException {
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(
                namedFactory("scheduled-failure-worker-")
        );
        AtomicInteger runs = new AtomicInteger();
        ScheduledFuture<?> periodic = null;
        try {
            periodic = executor.scheduleAtFixedRate(() -> {
                runs.incrementAndGet();
                throw new IllegalStateException("simulated periodic failure");
            }, 0, 10, TimeUnit.MILLISECONDS);

            boolean futureCompletedExceptionally = false;
            String failureType = null;
            try {
                periodic.get(2, TimeUnit.SECONDS);
            } catch (ExecutionException expected) {
                futureCompletedExceptionally = true;
                failureType = expected.getCause().getClass().getSimpleName();
            } catch (java.util.concurrent.TimeoutException e) {
                throw new IllegalStateException("Periodic failure không được quan sát đúng hạn.", e);
            }

            return map(
                    "runsObserved", runs.get(),
                    "futureDone", periodic.isDone(),
                    "futureCancelled", periodic.isCancelled(),
                    "futureCompletedExceptionally", futureCompletedExceptionally,
                    "failureType", failureType,
                    "laterExecutionsSuppressed", runs.get() == 1
            );
        } finally {
            if (periodic != null) periodic.cancel(true);
            shutdownAndAwait(executor);
        }
    }

    private Map<String, Object> observePeriodicSchedule(boolean fixedRate) throws InterruptedException {
        ScheduledExecutorService executor = Executors.newScheduledThreadPool(1, namedFactory("schedule-mode-"));
        int samples = 3;
        long intervalMillis = 30;
        long workMillis = 60;
        long[] starts = new long[samples];
        long[] ends = new long[samples];
        AtomicInteger sequence = new AtomicInteger();
        CountDownLatch completed = new CountDownLatch(samples);
        long origin = System.nanoTime();
        Runnable task = () -> {
            int index = sequence.getAndIncrement();
            if (index >= samples) return;
            starts[index] = System.nanoTime();
            try {
                Thread.sleep(workMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                ends[index] = System.nanoTime();
                completed.countDown();
            }
        };
        ScheduledFuture<?> periodic = null;
        try {
            periodic = fixedRate
                    ? executor.scheduleAtFixedRate(task, 0, intervalMillis, TimeUnit.MILLISECONDS)
                    : executor.scheduleWithFixedDelay(task, 0, intervalMillis, TimeUnit.MILLISECONDS);
            if (!completed.await(3, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Không quan sát đủ ba periodic execution.");
            }
            periodic.cancel(false);

            List<Map<String, Object>> timeline = new ArrayList<>();
            boolean noOverlap = true;
            for (int i = 0; i < samples; i++) {
                long gap = i == 0 ? 0 : starts[i] - ends[i - 1];
                if (i > 0 && gap < 0) noOverlap = false;
                timeline.add(map(
                        "run", i + 1,
                        "startMillis", (starts[i] - origin) / 1_000_000.0,
                        "endMillis", (ends[i] - origin) / 1_000_000.0,
                        "workMillis", (ends[i] - starts[i]) / 1_000_000.0,
                        "gapAfterPreviousEndMillis", i == 0 ? null : gap / 1_000_000.0
                ));
            }

            return map(
                    "mode", fixedRate ? "fixed-rate" : "fixed-delay",
                    "configuredIntervalMillis", intervalMillis,
                    "simulatedWorkMillis", workMillis,
                    "timeline", timeline,
                    "noOverlapObserved", noOverlap,
                    "periodicTaskCancelled", periodic.isCancelled()
            );
        } finally {
            if (periodic != null) periodic.cancel(true);
            shutdownAndAwait(executor);
        }
    }

    public Map<String, Object> forcedShutdownDemo() throws InterruptedException {
        ExecutorService executor = Executors.newSingleThreadExecutor(namedFactory("forced-shutdown-"));
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger interruptedWorkers = new AtomicInteger();
        AtomicInteger queuedTaskRuns = new AtomicInteger();
        Runnable queuedTask = queuedTaskRuns::incrementAndGet;
        try {
            executor.execute(() -> {
                started.countDown();
                try {
                    release.await();
                } catch (InterruptedException e) {
                    interruptedWorkers.incrementAndGet();
                    Thread.currentThread().interrupt();
                }
            });
            if (!started.await(2, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Forced-shutdown worker không start đúng hạn.");
            }
            executor.execute(queuedTask);
            executor.shutdown();
            boolean terminatedBeforeForce = executor.awaitTermination(50, TimeUnit.MILLISECONDS);
            List<Runnable> neverStarted = executor.shutdownNow();
            if (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Worker không hợp tác với shutdownNow.");
            }
            return map(
                    "terminatedBeforeForce", terminatedBeforeForce,
                    "interruptedWorkers", interruptedWorkers.get(),
                    "queuedTaskReturned", neverStarted.contains(queuedTask),
                    "neverStartedCount", neverStarted.size(),
                    "queuedTaskRuns", queuedTaskRuns.get(),
                    "terminatedAfterForce", executor.isTerminated()
            );
        } finally {
            executor.shutdownNow();
            release.countDown();
            shutdownAndAwait(executor);
        }
    }

    public Map<String, Object> callerRunsDemo() throws InterruptedException {
        CountDownLatch releaseWorker = new CountDownLatch(1);
        AtomicReference<String> fallbackThread = new AtomicReference<>();
        String callerThread = Thread.currentThread().getName();

        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                1,
                1,
                0,
                TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(1),
                namedFactory("caller-runs-worker-"),
                new ThreadPoolExecutor.CallerRunsPolicy()
        );

        try {
            executor.execute(() -> await(releaseWorker));
            waitForPoolSize(executor, 1);
            executor.execute(() -> { });
            waitForQueueSize(executor, 1);

            executor.execute(() -> fallbackThread.set(Thread.currentThread().getName()));
        } finally {
            releaseWorker.countDown();
            shutdownAndAwait(executor);
        }

        return map(
                "callerThread", callerThread,
                "fallbackThread", fallbackThread.get(),
                "ranOnCaller", callerThread.equals(fallbackThread.get())
        );
    }

    public Map<String, Object> discardPoliciesDemo() throws InterruptedException {
        return map(
                "discardPolicy", discardPolicySample(),
                "discardOldestPolicy", discardOldestPolicySample()
        );
    }

    public Map<String, Object> customRejectionHandlerDemo() throws InterruptedException {
        CountDownLatch releaseWorker = new CountDownLatch(1);
        AtomicInteger rejectionCount = new AtomicInteger();
        AtomicReference<String> rejectedByThread = new AtomicReference<>();

        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                1,
                1,
                0,
                TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(1),
                namedFactory("custom-reject-worker-"),
                (task, pool) -> {
                    rejectionCount.incrementAndGet();
                    rejectedByThread.set(Thread.currentThread().getName());
                    throw new RejectedExecutionException("simulated overload");
                }
        );

        boolean exceptionObserved = false;
        try {
            executor.execute(() -> await(releaseWorker));
            waitForPoolSize(executor, 1);
            executor.execute(() -> { });
            waitForQueueSize(executor, 1);

            try {
                executor.execute(() -> { });
            } catch (RejectedExecutionException expected) {
                exceptionObserved = true;
            }
        } finally {
            releaseWorker.countDown();
            shutdownAndAwait(executor);
        }

        return map(
                "rejectionCount", rejectionCount.get(),
                "rejectedByThread", rejectedByThread.get(),
                "exceptionObserved", exceptionObserved,
                "customHandlerCanRecordOverload", rejectionCount.get() == 1
        );
    }

    public Map<String, Object> gracefulShutdownDemo() throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(2, namedFactory("shutdown-worker-"));
        AtomicInteger completed = new AtomicInteger();

        try {
            for (int i = 0; i < 4; i++) {
                executor.execute(() -> {
                    try {
                        Thread.sleep(20);
                        completed.incrementAndGet();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });
            }
    
            executor.shutdown();
            boolean terminatedWithinTimeout = executor.awaitTermination(2, TimeUnit.SECONDS);
            if (!terminatedWithinTimeout) {
                executor.shutdownNow();
                if (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Executor không terminate sau forced shutdown.");
                }
            }
    
            return map(
                    "completedTasks", completed.get(),
                    "isShutdown", executor.isShutdown(),
                    "isTerminated", executor.isTerminated(),
                    "terminatedWithinTimeout", terminatedWithinTimeout
            );
        } finally {
            shutdownAndAwait(executor);
        }
    }

    private Map<String, Object> fixedPoolFactorySample() throws InterruptedException {
        Set<String> workers = ConcurrentHashMap.newKeySet();
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch started = new CountDownLatch(2);
        ExecutorService executor = Executors.newFixedThreadPool(2, namedFactory("factory-fixed-"));
        try {
            for (int i = 0; i < 4; i++) {
                executor.execute(() -> {
                    workers.add(Thread.currentThread().getName());
                    started.countDown();
                    await(release);
                });
            }
            if (!started.await(2, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Fixed pool không start đủ hai worker đúng thời gian dự kiến.");
            }
            return map(
                    "configuredWorkers", 2,
                    "activeWorkerNames", Set.copyOf(workers),
                    "tasksSubmitted", 4,
                    "tasksBeyondWorkersQueue", true
            );
        } finally {
            release.countDown();
            shutdownAndAwait(executor);
        }
    }

    private Map<String, Object> cachedPoolFactorySample() throws InterruptedException {
        Set<String> workers = ConcurrentHashMap.newKeySet();
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch started = new CountDownLatch(4);
        ExecutorService executor = Executors.newCachedThreadPool(namedFactory("factory-cached-"));
        try {
            for (int i = 0; i < 4; i++) {
                executor.execute(() -> {
                    workers.add(Thread.currentThread().getName());
                    started.countDown();
                    await(release);
                });
            }
            if (!started.await(2, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Cached pool không start đủ task đồng thời đúng thời gian dự kiến.");
            }
            return map(
                    "simultaneousTasks", 4,
                    "observedWorkers", workers.size(),
                    "growsWhenNoIdleWorker", workers.size() >= 4
            );
        } finally {
            release.countDown();
            shutdownAndAwait(executor);
        }
    }

    private Map<String, Object> singleThreadFactorySample() throws InterruptedException {
        List<Integer> executionOrder = java.util.Collections.synchronizedList(new ArrayList<>());
        CountDownLatch completed = new CountDownLatch(3);
        ExecutorService executor = Executors.newSingleThreadExecutor(namedFactory("factory-single-"));
        try {
            for (int i = 1; i <= 3; i++) {
                int task = i;
                executor.execute(() -> {
                    executionOrder.add(task);
                    completed.countDown();
                });
            }
            if (!completed.await(2, TimeUnit.SECONDS)) {
                throw new IllegalStateException("SingleThreadExecutor không hoàn thành đúng thời gian dự kiến.");
            }
            return map(
                    "executionOrder", List.copyOf(executionOrder),
                    "preservedSubmissionOrder", executionOrder.equals(List.of(1, 2, 3))
            );
        } finally {
            shutdownAndAwait(executor);
        }
    }

    private Map<String, Object> scheduledPoolFactorySample() throws InterruptedException {
        ScheduledExecutorService executor = Executors.newScheduledThreadPool(1, namedFactory("factory-scheduled-"));
        CountDownLatch ran = new CountDownLatch(1);
        AtomicReference<String> worker = new AtomicReference<>();
        long startedAt = System.nanoTime();
        try {
            executor.schedule(() -> {
                worker.set(Thread.currentThread().getName());
                ran.countDown();
            }, 50, TimeUnit.MILLISECONDS);

            boolean completed = ran.await(2, TimeUnit.SECONDS);
            long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
            return map(
                    "completed", completed,
                    "workerThread", worker.get(),
                    "ranAfterRequestedDelay", completed && elapsedMillis >= 40
            );
        } finally {
            shutdownAndAwait(executor);
        }
    }

    private Map<String, Object> discardPolicySample() throws InterruptedException {
        CountDownLatch releaseWorker = new CountDownLatch(1);
        AtomicInteger queuedTaskRuns = new AtomicInteger();
        AtomicInteger discardedTaskRuns = new AtomicInteger();

        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                1,
                1,
                0,
                TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(1),
                namedFactory("discard-worker-"),
                new ThreadPoolExecutor.DiscardPolicy()
        );

        try {
            executor.execute(() -> await(releaseWorker));
            waitForPoolSize(executor, 1);

            executor.execute(queuedTaskRuns::incrementAndGet);
            waitForQueueSize(executor, 1);

            executor.execute(discardedTaskRuns::incrementAndGet);
        } finally {
            releaseWorker.countDown();
            shutdownAndAwait(executor);
        }

        return map(
                "queuedTaskRuns", queuedTaskRuns.get(),
                "discardedTaskRuns", discardedTaskRuns.get(),
                "discardWasSilent", discardedTaskRuns.get() == 0
        );
    }

    private Map<String, Object> discardOldestPolicySample() throws InterruptedException {
        CountDownLatch releaseWorker = new CountDownLatch(1);
        AtomicInteger oldestQueuedTaskRuns = new AtomicInteger();
        AtomicInteger newestTaskRuns = new AtomicInteger();

        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                1,
                1,
                0,
                TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(1),
                namedFactory("discard-oldest-worker-"),
                new ThreadPoolExecutor.DiscardOldestPolicy()
        );

        try {
            executor.execute(() -> await(releaseWorker));
            waitForPoolSize(executor, 1);

            executor.execute(oldestQueuedTaskRuns::incrementAndGet);
            waitForQueueSize(executor, 1);

            executor.execute(newestTaskRuns::incrementAndGet);
        } finally {
            releaseWorker.countDown();
            shutdownAndAwait(executor);
        }

        return map(
                "oldestQueuedTaskRuns", oldestQueuedTaskRuns.get(),
                "newestTaskRuns", newestTaskRuns.get(),
                "oldestWasReplaced", oldestQueuedTaskRuns.get() == 0 && newestTaskRuns.get() == 1
        );
    }

    private static Map<String, Object> snapshot(String phase, ThreadPoolExecutor executor) {
        return map(
                "phase", phase,
                "poolSize", executor.getPoolSize(),
                "activeCount", executor.getActiveCount(),
                "queueSize", executor.getQueue().size()
        );
    }

    private static ThreadFactory namedFactory(String prefix) {
        AtomicInteger sequence = new AtomicInteger();
        return runnable -> new Thread(runnable, prefix + sequence.incrementAndGet());
    }

    private static void shutdownAndAwait(ExecutorService executor) throws InterruptedException {
        boolean interrupted = Thread.interrupted();
        executor.shutdown();
        boolean forced = interrupted;
        if (forced) executor.shutdownNow();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        try {
            while (!executor.isTerminated()) {
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) {
                    if (forced) throw new IllegalStateException("Executor không terminate sau cleanup.");
                    executor.shutdownNow();
                    forced = true;
                    deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
                    continue;
                }
                try {
                    executor.awaitTermination(remaining, TimeUnit.NANOSECONDS);
                } catch (InterruptedException e) {
                    interrupted = true;
                    executor.shutdownNow();
                    forced = true;
                }
            }
        } finally {
            if (interrupted) Thread.currentThread().interrupt();
        }
    }

    private static void waitForPoolSize(ThreadPoolExecutor executor, int expected) throws InterruptedException {
        waitUntil(() -> executor.getPoolSize() >= expected, "poolSize=" + expected);
    }

    private static void waitForQueueSize(ThreadPoolExecutor executor, int expected) throws InterruptedException {
        waitUntil(() -> executor.getQueue().size() >= expected, "queueSize=" + expected);
    }

    private static void waitUntil(Check check, String description) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (System.nanoTime() < deadline) {
            if (check.test()) return;
            Thread.sleep(5);
        }
        throw new IllegalStateException("Không đạt trạng thái mong đợi: " + description);
    }

    private static boolean await(CountDownLatch latch) {
        try {
            latch.await();
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static Map<String, Object> map(Object... pairs) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            result.put((String) pairs[i], pairs[i + 1]);
        }
        return result;
    }

    @FunctionalInterface
    private interface Check {
        boolean test();
    }
}
