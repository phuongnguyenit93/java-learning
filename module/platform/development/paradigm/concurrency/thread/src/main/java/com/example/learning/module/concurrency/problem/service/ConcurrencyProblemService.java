package com.example.learning.module.concurrency.problem.service;

import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

@Service
public class ConcurrencyProblemService {

    private static final Duration THREAD_TIMEOUT = Duration.ofSeconds(2);

    public Map<String, Object> deterministicRaceCondition()
            throws InterruptedException {

        PlainCounter counter = new PlainCounter();

        AtomicInteger snapshotA = new AtomicInteger();
        AtomicInteger snapshotB = new AtomicInteger();

        CountDownLatch bothRead = new CountDownLatch(2);

        Thread threadA = new Thread(() -> {
            int snapshot = counter.value;
            snapshotA.set(snapshot);

            bothRead.countDown();
            awaitLatch(bothRead);

            counter.value = snapshot + 1;
        }, "race-worker-a");

        Thread threadB = new Thread(() -> {
            int snapshot = counter.value;
            snapshotB.set(snapshot);

            bothRead.countDown();
            awaitLatch(bothRead);

            counter.value = snapshot + 1;
        }, "race-worker-b");

        try {
            threadA.start();
            threadB.start();
            joinOrFail(threadA);
            joinOrFail(threadB);
        } finally {
            cleanupThreads(threadA, threadB);
        }

        int expectedSequentialResult = 2;
        int actualResult = counter.value;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("snapshotA", snapshotA.get());
        result.put("snapshotB", snapshotB.get());
        result.put("expectedSequentialResult", expectedSequentialResult);
        result.put("actualResult", actualResult);
        result.put("lostUpdates", expectedSequentialResult - actualResult);
        result.put("workersTerminated", !threadA.isAlive() && !threadB.isAlive());

        return result;
    }

    public Map<String, Object> startJoinHappensBefore()
            throws InterruptedException {

        StartJoinState state = new StartJoinState();
        state.beforeStart = 7;

        Thread worker = new Thread(() -> {
            state.observedByWorker = state.beforeStart;
            state.writtenByWorker = 42;
        }, "happens-before-worker");

        try {
            worker.start();
            joinOrFail(worker);
        } finally {
            cleanupThreads(worker);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("valueWrittenBeforeStart", 7);
        result.put("workerObservedBeforeStart", state.observedByWorker);
        result.put("valueWrittenByWorker", 42);
        result.put("parentObservedAfterJoin", state.writtenByWorker);
        result.put("workerState", worker.getState().name());

        return result;
    }

    public Map<String, Object> volatilePublication()
            throws InterruptedException {

        PublicationState state = new PublicationState();
        Thread reader = new Thread(() -> {
            while (!state.ready) {
                if (Thread.currentThread().isInterrupted()) {
                    return;
                }

                Thread.onSpinWait();
            }

            state.readerObservedData = state.data;
        }, "volatile-publication-reader");

        Thread writer = new Thread(() -> {
            state.data = 42;
            state.ready = true;
        }, "volatile-publication-writer");

        try {
            reader.start();
            writer.start();
            joinOrFail(writer);
            joinOrFail(reader);
        } finally {
            cleanupThreads(reader, writer);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("publishedData", state.data);
        result.put("ready", state.ready);
        result.put("readerObservedData", state.readerObservedData);
        result.put("readerTerminated", !reader.isAlive());
        result.put("writerTerminated", !writer.isAlive());

        return result;
    }

    public Map<String, Object> volatileIsNotAtomic()
            throws InterruptedException {

        VolatileCounter counter = new VolatileCounter();

        AtomicInteger snapshotA = new AtomicInteger();
        AtomicInteger snapshotB = new AtomicInteger();

        CountDownLatch bothRead = new CountDownLatch(2);

        Thread threadA = new Thread(() -> {
            int snapshot = counter.value;
            snapshotA.set(snapshot);

            bothRead.countDown();
            awaitLatch(bothRead);

            counter.value = snapshot + 1;
        }, "volatile-race-worker-a");

        Thread threadB = new Thread(() -> {
            int snapshot = counter.value;
            snapshotB.set(snapshot);

            bothRead.countDown();
            awaitLatch(bothRead);

            counter.value = snapshot + 1;
        }, "volatile-race-worker-b");

        try {
            threadA.start();
            threadB.start();
            joinOrFail(threadA);
            joinOrFail(threadB);
        } finally {
            cleanupThreads(threadA, threadB);
        }

        int expectedSequentialResult = 2;
        int actualResult = counter.value;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("fieldIsVolatile", true);
        result.put("snapshotA", snapshotA.get());
        result.put("snapshotB", snapshotB.get());
        result.put("expectedSequentialResult", expectedSequentialResult);
        result.put("actualResult", actualResult);
        result.put("lostUpdates", expectedSequentialResult - actualResult);

        return result;
    }

    public Map<String, Object> deadlockRisk()
            throws InterruptedException {

        ReentrantLock lockA = new ReentrantLock();
        ReentrantLock lockB = new ReentrantLock();

        CountDownLatch bothHoldingFirstLock = new CountDownLatch(2);
        CountDownLatch bothTriedSecondLock = new CountDownLatch(2);

        AtomicBoolean threadAFirstLock = new AtomicBoolean(false);
        AtomicBoolean threadBFirstLock = new AtomicBoolean(false);
        AtomicBoolean threadASecondLock = new AtomicBoolean(false);
        AtomicBoolean threadBSecondLock = new AtomicBoolean(false);

        Thread threadA = new Thread(() -> {
            lockA.lock();
            threadAFirstLock.set(true);

            try {
                bothHoldingFirstLock.countDown();
                awaitLatch(bothHoldingFirstLock);

                boolean acquired = lockB.tryLock();
                threadASecondLock.set(acquired);

                if (acquired) {
                    lockB.unlock();
                }

                bothTriedSecondLock.countDown();
                awaitLatch(bothTriedSecondLock);
            } finally {
                lockA.unlock();
            }
        }, "deadlock-risk-worker-a");

        Thread threadB = new Thread(() -> {
            lockB.lock();
            threadBFirstLock.set(true);

            try {
                bothHoldingFirstLock.countDown();
                awaitLatch(bothHoldingFirstLock);

                boolean acquired = lockA.tryLock();
                threadBSecondLock.set(acquired);

                if (acquired) {
                    lockA.unlock();
                }

                bothTriedSecondLock.countDown();
                awaitLatch(bothTriedSecondLock);
            } finally {
                lockB.unlock();
            }
        }, "deadlock-risk-worker-b");

        try {
            threadA.start();
            threadB.start();
            joinOrFail(threadA);
            joinOrFail(threadB);
        } finally {
            cleanupThreads(threadA, threadB);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("threadAHasFirstLock", threadAFirstLock.get());
        result.put("threadBHasFirstLock", threadBFirstLock.get());
        result.put("threadASecondLockAcquired", threadASecondLock.get());
        result.put("threadBSecondLockAcquired", threadBSecondLock.get());
        result.put(
                "circularDependencyObserved",
                threadAFirstLock.get()
                        && threadBFirstLock.get()
                        && !threadASecondLock.get()
                        && !threadBSecondLock.get()
        );
        result.put("recoveredWithoutLeak", !threadA.isAlive() && !threadB.isAlive());

        return result;
    }

    public Map<String, Object> boundedLivelock()
            throws InterruptedException {

        int rounds = 5;

        AtomicBoolean workerATrying = new AtomicBoolean(false);
        AtomicBoolean workerBTrying = new AtomicBoolean(false);
        AtomicInteger workerABackoffs = new AtomicInteger();
        AtomicInteger workerBBackoffs = new AtomicInteger();
        AtomicBoolean progressMade = new AtomicBoolean(false);

        CyclicBarrier phaseBarrier = new CyclicBarrier(2);

        Thread workerA = new Thread(() -> {
            for (int round = 0; round < rounds; round++) {
                workerATrying.set(true);

                if (!awaitBarrier(phaseBarrier)) {
                    return;
                }

                boolean collision = workerBTrying.get();

                if (!awaitBarrier(phaseBarrier)) {
                    return;
                }

                if (collision) {
                    workerATrying.set(false);
                    workerABackoffs.incrementAndGet();
                } else {
                    progressMade.set(true);
                    return;
                }

                if (!awaitBarrier(phaseBarrier)) {
                    return;
                }
            }
        }, "livelock-worker-a");

        Thread workerB = new Thread(() -> {
            for (int round = 0; round < rounds; round++) {
                workerBTrying.set(true);

                if (!awaitBarrier(phaseBarrier)) {
                    return;
                }

                boolean collision = workerATrying.get();

                if (!awaitBarrier(phaseBarrier)) {
                    return;
                }

                if (collision) {
                    workerBTrying.set(false);
                    workerBBackoffs.incrementAndGet();
                } else {
                    progressMade.set(true);
                    return;
                }

                if (!awaitBarrier(phaseBarrier)) {
                    return;
                }
            }
        }, "livelock-worker-b");

        try {
            workerA.start();
            workerB.start();
            joinOrFail(workerA);
            joinOrFail(workerB);
        } finally {
            cleanupThreads(workerA, workerB);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("rounds", rounds);
        result.put("workerABackoffs", workerABackoffs.get());
        result.put("workerBBackoffs", workerBBackoffs.get());
        result.put("progressMade", progressMade.get());
        result.put("workersTerminated", !workerA.isAlive() && !workerB.isAlive());

        return result;
    }

    public Map<String, Object> deadlockPrevention()
            throws InterruptedException {

        ReentrantLock lockA = new ReentrantLock();
        ReentrantLock lockB = new ReentrantLock();
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger completed = new AtomicInteger();

        Runnable orderedTask = () -> {
            awaitLatch(start);
            lockA.lock();
            try {
                lockB.lock();
                try {
                    completed.incrementAndGet();
                } finally {
                    lockB.unlock();
                }
            } finally {
                lockA.unlock();
            }
        };

        Thread threadA = new Thread(orderedTask, "deadlock-prevention-a");
        Thread threadB = new Thread(orderedTask, "deadlock-prevention-b");

        try {
            threadA.start();
            threadB.start();
            start.countDown();
            joinOrFail(threadA);
            joinOrFail(threadB);
        } finally {
            start.countDown();
            cleanupThreads(threadA, threadB);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("lockOrder", "A -> B");
        result.put("completedWorkers", completed.get());
        result.put("circularWaitPossibleByDesign", false);
        result.put("workersTerminated", !threadA.isAlive() && !threadB.isAlive());
        return result;
    }

    private static void joinOrFail(Thread thread)
            throws InterruptedException {

        thread.join(THREAD_TIMEOUT.toMillis());

        if (thread.isAlive()) {
            thread.interrupt();
            thread.join(THREAD_TIMEOUT.toMillis());

            throw new IllegalStateException(
                    "Thread không kết thúc đúng thời gian dự kiến: "
                            + thread.getName()
            );
        }
    }

    private static void cleanupThreads(Thread... threads) throws InterruptedException {
        for (Thread thread : threads) {
            if (thread != null && thread.isAlive()) {
                thread.interrupt();
            }
        }

        for (Thread thread : threads) {
            if (thread == null || !thread.isAlive()) {
                continue;
            }
            thread.join(THREAD_TIMEOUT.toMillis());
            if (thread.isAlive()) {
                throw new IllegalStateException("Thread vẫn còn sống sau cleanup: " + thread.getName());
            }
        }
    }

    private static boolean awaitLatch(CountDownLatch latch) {
        try {
            latch.await();
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static boolean awaitBarrier(CyclicBarrier barrier) {
        try {
            barrier.await();
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (BrokenBarrierException e) {
            return false;
        }
    }

    private static class PlainCounter {
        int value;
    }

    private static class VolatileCounter {
        volatile int value;
    }

    private static class StartJoinState {
        int beforeStart;
        int observedByWorker;
        int writtenByWorker;
    }

    private static class PublicationState {
        int data;
        int readerObservedData = -1;
        volatile boolean ready;
    }
}
