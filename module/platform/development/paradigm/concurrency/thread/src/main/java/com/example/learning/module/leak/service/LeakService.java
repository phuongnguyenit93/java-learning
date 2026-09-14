package com.example.learning.module.leak.service;

import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class LeakService {

    public Map<String, Object> threadLeakPatternDemo() throws InterruptedException {
        CountDownLatch stop = new CountDownLatch(1);
        Thread worker = new Thread(() -> await(stop), "bounded-leak-demo-thread");
        boolean aliveBeforeCleanup;
        try {
            worker.start();
            waitUntilAlive(worker);
            aliveBeforeCleanup = worker.isAlive();
        } finally {
            stop.countDown();
            boolean interrupted = Thread.interrupted();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
            try {
                while (worker.isAlive() && System.nanoTime() < deadline) {
                    try {
                        worker.join(Math.max(1, TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime())));
                    } catch (InterruptedException e) {
                        interrupted = true;
                        worker.interrupt();
                    }
                }
            } finally {
                if (interrupted) Thread.currentThread().interrupt();
            }
            if (worker.isAlive()) {
                throw new IllegalStateException("Leak demo worker không terminate sau cleanup.");
            }
        }

        return map(
                "aliveBeforeCleanup", aliveBeforeCleanup,
                "wouldLeakIfNeverReleased", aliveBeforeCleanup,
                "aliveAfterCleanup", worker.isAlive(),
                "finalState", worker.getState().name()
        );
    }

    public Map<String, Object> poolLeakPatternDemo() throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch tasksDone = new CountDownLatch(2);

        try {
            pool.execute(tasksDone::countDown);
            pool.execute(tasksDone::countDown);
            if (!tasksDone.await(2, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Pool leak demo task không hoàn thành đúng hạn.");
            }
            return map(
                    "isShutdownBeforeCleanup", pool.isShutdown(),
                    "patternWouldLeakIfOwnerForgotShutdown", !pool.isShutdown()
            );
        } finally {
            pool.shutdown();
            if (!pool.awaitTermination(2, TimeUnit.SECONDS)) {
                pool.shutdownNow();
                if (!pool.awaitTermination(2, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Pool leak demo executor không terminate sau cleanup.");
                }
            }
        }
    }

    public Map<String, Object> queuePressureDemo() throws InterruptedException {
        CountDownLatch releaseWorker = new CountDownLatch(1);
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                1,
                1,
                0,
                TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(3),
                new ThreadPoolExecutor.AbortPolicy()
        );

        AtomicInteger rejected = new AtomicInteger();
        try {
            executor.execute(() -> await(releaseWorker));
            waitForActive(executor);

            for (int i = 0; i < 4; i++) {
                try {
                    executor.execute(() -> { });
                } catch (RejectedExecutionException e) {
                    rejected.incrementAndGet();
                }
            }

            return map(
                    "queueCapacity", 3,
                    "queueSizeAtPressure", executor.getQueue().size(),
                    "rejectedTasks", rejected.get(),
                    "boundedQueuePreventsUnboundedBacklog", true
            );
        } finally {
            releaseWorker.countDown();
            executor.shutdown();
            if (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
                executor.shutdownNow();
                if (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Queue pressure executor không terminate sau cleanup.");
                }
            }
        }
    }

    private static void waitUntilAlive(Thread worker) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
        while (System.nanoTime() < deadline) {
            if (worker.isAlive()) return;
            Thread.sleep(5);
        }
        throw new IllegalStateException("Worker không start");
    }

    private static void waitForActive(ThreadPoolExecutor executor) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
        while (System.nanoTime() < deadline) {
            if (executor.getActiveCount() == 1) return;
            Thread.sleep(5);
        }
        throw new IllegalStateException("Executor chưa có active worker");
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
}
