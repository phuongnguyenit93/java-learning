package com.example.learning.module.experiment.service;

import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.concurrent.SimpleAsyncTaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class ConcurrencyExperimentService {

    public Map<String, Object> executorSaturationDemo() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(1);
        executor.setThreadNamePrefix("saturation-demo-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.initialize();

        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch thirdStarted = new CountDownLatch(1);
        CountDownLatch releaseWorkers = new CountDownLatch(1);
        CountDownLatch firstFinished = new CountDownLatch(1);
        CountDownLatch thirdFinished = new CountDownLatch(1);
        CountDownLatch queuedFinished = new CountDownLatch(1);
        AtomicInteger completed = new AtomicInteger();

        try {
            executor.execute(() -> blockingTask(
                    firstStarted,
                    releaseWorkers,
                    firstFinished,
                    completed
            ));
            await(firstStarted, "First saturation task did not start");

            executor.execute(() -> {
                completed.incrementAndGet();
                queuedFinished.countDown();
            });

            int queueSizeAfterSecondSubmit =
                    executor.getThreadPoolExecutor().getQueue().size();
            if (queueSizeAfterSecondSubmit != 1) {
                throw new IllegalStateException(
                        "Second task did not occupy the expected queue slot"
                );
            }

            executor.execute(() -> blockingTask(
                    thirdStarted,
                    releaseWorkers,
                    thirdFinished,
                    completed
            ));
            await(thirdStarted, "Third saturation task did not grow the pool");

            int activeBeforeFourthSubmit = executor.getActiveCount();
            int poolSizeBeforeFourthSubmit = executor.getPoolSize();
            int queueSizeBeforeFourthSubmit =
                    executor.getThreadPoolExecutor().getQueue().size();

            boolean fourthTaskRejected = false;
            String rejectionType = null;

            try {
                executor.execute(completed::incrementAndGet);
            } catch (TaskRejectedException expected) {
                fourthTaskRejected = true;
                rejectionType = expected.getClass().getSimpleName();
            }

            releaseWorkers.countDown();
            await(firstFinished, "First saturation task did not finish");
            await(thirdFinished, "Third saturation task did not finish");
            await(queuedFinished, "Queued saturation task did not finish");

            return map(
                    "corePoolSize", 1,
                    "maxPoolSize", 2,
                    "queueCapacity", 1,
                    "queueSizeAfterSecondSubmit", queueSizeAfterSecondSubmit,
                    "activeBeforeFourthSubmit", activeBeforeFourthSubmit,
                    "poolSizeBeforeFourthSubmit", poolSizeBeforeFourthSubmit,
                    "queueSizeBeforeFourthSubmit", queueSizeBeforeFourthSubmit,
                    "poolGrewAfterQueueFilled", poolSizeBeforeFourthSubmit == 2,
                    "fourthTaskRejected", fourthTaskRejected,
                    "rejectionType", rejectionType,
                    "completedAcceptedTasks", completed.get(),
                    "acceptedTasksDrained", completed.get() == 3
            );
        } finally {
            releaseWorkers.countDown();
            executor.shutdown();
            awaitTermination(executor.getThreadPoolExecutor());
        }
    }

    public Map<String, Object> schedulerExecutionModelsDemo() {
        ThreadPoolTaskScheduler pooledScheduler = new ThreadPoolTaskScheduler();
        pooledScheduler.setPoolSize(2);
        pooledScheduler.setThreadNamePrefix("pooled-scheduler-");
        pooledScheduler.initialize();

        AtomicInteger targetThreadIndex = new AtomicInteger();
        ExecutorService simpleTargetExecutor = Executors.newCachedThreadPool(
                runnable -> new Thread(
                        runnable,
                        "simple-target-" + targetThreadIndex.incrementAndGet()
                )
        );
        SimpleAsyncTaskScheduler simpleScheduler = new SimpleAsyncTaskScheduler();
        simpleScheduler.setThreadNamePrefix("simple-clock-");
        simpleScheduler.setTargetTaskExecutor(simpleTargetExecutor);

        AtomicInteger pooledActive = new AtomicInteger();
        AtomicInteger pooledMaxConcurrent = new AtomicInteger();
        AtomicInteger pooledRuns = new AtomicInteger();
        Set<String> pooledThreads = ConcurrentHashMap.newKeySet();
        CountDownLatch pooledCompleted = new CountDownLatch(3);

        AtomicInteger simpleActive = new AtomicInteger();
        AtomicInteger simpleMaxConcurrent = new AtomicInteger();
        AtomicInteger simpleStarted = new AtomicInteger();
        Set<String> simpleThreads = ConcurrentHashMap.newKeySet();
        CountDownLatch simpleThreeStarted = new CountDownLatch(3);
        CountDownLatch releaseSimpleRate = new CountDownLatch(1);
        CountDownLatch simpleThreeCompleted = new CountDownLatch(3);

        AtomicReference<String> simpleFixedDelayThread = new AtomicReference<>();
        CountDownLatch simpleFixedDelayRan = new CountDownLatch(1);

        ScheduledFuture<?> pooledRate = null;
        ScheduledFuture<?> simpleRate = null;
        ScheduledFuture<?> simpleDelay = null;

        try {
            pooledRate = pooledScheduler.scheduleAtFixedRate(() -> {
                int active = pooledActive.incrementAndGet();
                pooledMaxConcurrent.accumulateAndGet(active, Math::max);
                pooledThreads.add(Thread.currentThread().getName());
                try {
                    sleep(80);
                } finally {
                    pooledRuns.incrementAndGet();
                    pooledActive.decrementAndGet();
                    pooledCompleted.countDown();
                }
            }, Duration.ofMillis(20));

            await(pooledCompleted, "ThreadPoolTaskScheduler did not complete three runs");
            pooledRate.cancel(true);

            simpleRate = simpleScheduler.scheduleAtFixedRate(() -> {
                int run = simpleStarted.incrementAndGet();
                if (run > 3) {
                    return;
                }

                int active = simpleActive.incrementAndGet();
                simpleMaxConcurrent.accumulateAndGet(active, Math::max);
                simpleThreads.add(Thread.currentThread().getName());
                simpleThreeStarted.countDown();
                try {
                    releaseSimpleRate.await();
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                } finally {
                    simpleActive.decrementAndGet();
                    simpleThreeCompleted.countDown();
                }
            }, Duration.ofMillis(20));

            await(simpleThreeStarted, "SimpleAsyncTaskScheduler did not overlap fixed-rate firings");
            releaseSimpleRate.countDown();
            await(simpleThreeCompleted, "SimpleAsyncTaskScheduler fixed-rate tasks did not finish");
            simpleRate.cancel(true);

            simpleDelay = simpleScheduler.scheduleWithFixedDelay(() -> {
                simpleFixedDelayThread.compareAndSet(
                        null,
                        Thread.currentThread().getName()
                );
                simpleFixedDelayRan.countDown();
            }, Duration.ofMillis(100));

            await(simpleFixedDelayRan, "SimpleAsyncTaskScheduler fixed-delay task did not run");
            simpleDelay.cancel(true);

            String fixedDelayThread = simpleFixedDelayThread.get();

            return map(
                    "threadPoolFixedRateRunsObserved", pooledRuns.get(),
                    "threadPoolFixedRateMaxConcurrent", pooledMaxConcurrent.get(),
                    "threadPoolSameRegistrationOverlapped",
                    pooledMaxConcurrent.get() > 1,
                    "threadPoolThreads", Set.copyOf(pooledThreads),
                    "simpleFixedRateMaxConcurrent", simpleMaxConcurrent.get(),
                    "simpleFixedRateOverlapped", simpleMaxConcurrent.get() > 1,
                    "simpleFixedRateThreads", Set.copyOf(simpleThreads),
                    "simpleFixedRateUsedTargetExecutor",
                    simpleThreads.stream().allMatch(name -> name.startsWith("simple-target-")),
                    "simpleFixedDelayThread", fixedDelayThread,
                    "simpleFixedDelayUsedSchedulerThread",
                    fixedDelayThread != null && fixedDelayThread.startsWith("simple-clock-")
            );
        } finally {
            releaseSimpleRate.countDown();
            if (pooledRate != null) {
                pooledRate.cancel(true);
            }
            if (simpleRate != null) {
                simpleRate.cancel(true);
            }
            if (simpleDelay != null) {
                simpleDelay.cancel(true);
            }
            pooledScheduler.shutdown();
            awaitTermination(pooledScheduler.getScheduledThreadPoolExecutor());
            simpleScheduler.close();
            simpleTargetExecutor.shutdownNow();
            awaitTermination(simpleTargetExecutor);
        }
    }

    private static void blockingTask(
            CountDownLatch started,
            CountDownLatch release,
            CountDownLatch finished,
            AtomicInteger completed
    ) {
        started.countDown();
        try {
            release.await();
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
        } finally {
            completed.incrementAndGet();
            finished.countDown();
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
        }
    }

    private static void await(CountDownLatch latch, String message) {
        try {
            if (!latch.await(2, TimeUnit.SECONDS)) {
                throw new IllegalStateException(message);
            }
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(message, error);
        }
    }

    private static void awaitTermination(ExecutorService executor) {
        try {
            if (!executor.awaitTermination(1, TimeUnit.SECONDS)) {
                executor.shutdownNow();
                executor.awaitTermination(1, TimeUnit.SECONDS);
            }
        } catch (InterruptedException error) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private static Map<String, Object> map(Object... pairs) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index < pairs.length; index += 2) {
            result.put((String) pairs[index], pairs[index + 1]);
        }
        return result;
    }
}
