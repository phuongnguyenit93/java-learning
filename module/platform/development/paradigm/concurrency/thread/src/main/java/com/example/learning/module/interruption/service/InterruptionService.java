package com.example.learning.module.interruption.service;

import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class InterruptionService {

    private static final Duration WAIT_TIMEOUT = Duration.ofSeconds(2);

    public Map<String, Object> interruptBusyWorker() throws InterruptedException {
        CountDownLatch started = new CountDownLatch(1);
        AtomicLong iterations = new AtomicLong();
        AtomicBoolean interruptObserved = new AtomicBoolean(false);

        Thread worker = new Thread(() -> {
            started.countDown();

            while (!Thread.currentThread().isInterrupted()) {
                iterations.incrementAndGet();
                Thread.onSpinWait();
            }

            interruptObserved.set(true);
        }, "interrupt-busy-worker");

        try {
            worker.start();
            await(started, "Busy worker không start đúng thời gian dự kiến.");
            worker.interrupt();
            join(worker);
        } finally {
            cleanup(worker);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("interruptObserved", interruptObserved.get());
        result.put("iterationsBeforeStop", iterations.get());
        result.put("workerAlive", worker.isAlive());
        result.put("finalState", worker.getState().name());
        return result;
    }

    public Map<String, Object> inspectInterruptFlag() throws InterruptedException {
        CountDownLatch started = new CountDownLatch(1);
        AtomicBoolean proceed = new AtomicBoolean(false);

        AtomicBoolean firstIsInterrupted = new AtomicBoolean();
        AtomicBoolean secondIsInterrupted = new AtomicBoolean();
        AtomicBoolean firstInterrupted = new AtomicBoolean();
        AtomicBoolean secondInterrupted = new AtomicBoolean();
        AtomicBoolean finalIsInterrupted = new AtomicBoolean();

        Thread worker = new Thread(() -> {
            started.countDown();

            while (!proceed.get()) {
                Thread.onSpinWait();
            }

            firstIsInterrupted.set(Thread.currentThread().isInterrupted());
            secondIsInterrupted.set(Thread.currentThread().isInterrupted());
            firstInterrupted.set(Thread.interrupted());
            secondInterrupted.set(Thread.interrupted());
            finalIsInterrupted.set(Thread.currentThread().isInterrupted());
        }, "interrupt-flag-worker");

        try {
            worker.start();
            await(started, "Flag worker không start đúng thời gian dự kiến.");
            worker.interrupt();
            proceed.set(true);
            join(worker);
        } finally {
            proceed.set(true);
            cleanup(worker);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("firstIsInterrupted", firstIsInterrupted.get());
        result.put("secondIsInterrupted", secondIsInterrupted.get());
        result.put("firstThreadInterrupted", firstInterrupted.get());
        result.put("secondThreadInterrupted", secondInterrupted.get());
        result.put("finalIsInterrupted", finalIsInterrupted.get());
        return result;
    }

    public Map<String, Object> interruptSleepingWorker() throws InterruptedException {
        CountDownLatch readyToSleep = new CountDownLatch(1);
        AtomicBoolean interruptedExceptionCaught = new AtomicBoolean(false);
        AtomicBoolean flagBeforeRestore = new AtomicBoolean(true);
        AtomicBoolean flagAfterRestore = new AtomicBoolean(false);

        Thread worker = new Thread(() -> {
            try {
                readyToSleep.countDown();
                Thread.sleep(30_000);
            } catch (InterruptedException e) {
                interruptedExceptionCaught.set(true);
                flagBeforeRestore.set(Thread.currentThread().isInterrupted());

                Thread.currentThread().interrupt();
                flagAfterRestore.set(Thread.currentThread().isInterrupted());
            }
        }, "interrupt-sleep-worker");

        try {
            worker.start();
            await(readyToSleep, "Sleep worker không start đúng thời gian dự kiến.");
            worker.interrupt();
            join(worker);
        } finally {
            cleanup(worker);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("interruptedExceptionCaught", interruptedExceptionCaught.get());
        result.put("flagBeforeRestore", flagBeforeRestore.get());
        result.put("flagAfterRestore", flagAfterRestore.get());
        result.put("workerAlive", worker.isAlive());
        result.put("finalState", worker.getState().name());
        return result;
    }

    public Map<String, Object> cancelWithCleanup() throws InterruptedException {
        CountDownLatch resourceOpened = new CountDownLatch(1);
        AtomicBoolean resourceOpen = new AtomicBoolean(false);
        AtomicBoolean interruptionObserved = new AtomicBoolean(false);
        AtomicBoolean cleanupCalled = new AtomicBoolean(false);

        Thread worker = new Thread(() -> {
            resourceOpen.set(true);
            resourceOpened.countDown();

            try {
                while (!Thread.currentThread().isInterrupted()) {
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        interruptionObserved.set(true);
                        Thread.currentThread().interrupt();
                    }
                }
                // Cancellation có thể được quan sát ở điều kiện loop, không chỉ trong catch.
                interruptionObserved.set(Thread.currentThread().isInterrupted());
            } finally {
                resourceOpen.set(false);
                cleanupCalled.set(true);
            }
        }, "interrupt-cleanup-worker");

        try {
            worker.start();
            await(resourceOpened, "Cleanup worker không mở resource đúng thời gian dự kiến.");
            worker.interrupt();
            join(worker);
        } finally {
            cleanup(worker);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("interruptionObserved", interruptionObserved.get());
        result.put("cleanupCalled", cleanupCalled.get());
        result.put("resourceOpen", resourceOpen.get());
        result.put("workerAlive", worker.isAlive());
        result.put("finalState", worker.getState().name());
        return result;
    }

    public Map<String, Object> interruptSynchronizedWaiter() throws InterruptedException {
        Object monitor = new Object();
        CountDownLatch attemptingMonitor = new CountDownLatch(1);

        AtomicBoolean acquiredMonitor = new AtomicBoolean(false);
        AtomicBoolean interruptedWhenAcquired = new AtomicBoolean(false);

        Thread worker = new Thread(() -> {
            attemptingMonitor.countDown();

            synchronized (monitor) {
                acquiredMonitor.set(true);
                interruptedWhenAcquired.set(Thread.currentThread().isInterrupted());
            }
        }, "interrupt-synchronized-worker");

        Thread.State stateBeforeInterrupt;
        Thread.State stateAfterInterrupt;
        boolean interruptFlagWhileBlocked;

        try {
            synchronized (monitor) {
                worker.start();
                await(attemptingMonitor, "Synchronized worker không bắt đầu đúng thời gian dự kiến.");
                waitForState(worker, Thread.State.BLOCKED);

                stateBeforeInterrupt = worker.getState();
                worker.interrupt();

                waitForState(worker, Thread.State.BLOCKED);
                stateAfterInterrupt = worker.getState();
                interruptFlagWhileBlocked = worker.isInterrupted();
            }

            join(worker);
        } finally {
            cleanup(worker);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("stateBeforeInterrupt", stateBeforeInterrupt.name());
        result.put("stateAfterInterrupt", stateAfterInterrupt.name());
        result.put("interruptFlagWhileBlocked", interruptFlagWhileBlocked);
        result.put("acquiredMonitorAfterRelease", acquiredMonitor.get());
        result.put("interruptFlagWhenMonitorAcquired", interruptedWhenAcquired.get());
        result.put("workerAlive", worker.isAlive());
        result.put("finalState", worker.getState().name());
        return result;
    }

    public Map<String, Object> stopFlagVsInterrupt() throws InterruptedException {
        StopSignal stopSignal = new StopSignal();
        CountDownLatch blockingOperationEntered = new CountDownLatch(1);
        AtomicBoolean interruptedExceptionCaught = new AtomicBoolean(false);

        Thread worker = new Thread(() -> {
            while (!stopSignal.stopRequested) {
                try {
                    blockingOperationEntered.countDown();
                    Thread.sleep(30_000);
                } catch (InterruptedException e) {
                    interruptedExceptionCaught.set(true);
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }, "stop-flag-vs-interrupt-worker");

        boolean aliveAfterOnlyStopFlag;
        Thread.State stateAfterOnlyStopFlag;
        try {
            worker.start();
            await(blockingOperationEntered, "Worker không đi vào blocking operation đúng thời gian dự kiến.");
            waitForState(worker, Thread.State.TIMED_WAITING);

            stopSignal.stopRequested = true;
            aliveAfterOnlyStopFlag = worker.isAlive();
            stateAfterOnlyStopFlag = worker.getState();

            // Stop flag không đánh thức sleep(). Interrupt được dùng để kết thúc demo ngay và cleanup deterministic.
            worker.interrupt();
            join(worker);
        } finally {
            stopSignal.stopRequested = true;
            cleanup(worker);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("stopFlagVisible", stopSignal.stopRequested);
        result.put("aliveAfterOnlyStopFlag", aliveAfterOnlyStopFlag);
        result.put("stateAfterOnlyStopFlag", stateAfterOnlyStopFlag.name());
        result.put("interruptWokeBlockingOperation", interruptedExceptionCaught.get());
        result.put("workerAliveAfterCleanup", worker.isAlive());
        result.put("finalState", worker.getState().name());
        return result;
    }

    private static void await(CountDownLatch latch, String errorMessage)
            throws InterruptedException {
        if (!latch.await(WAIT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
            throw new IllegalStateException(errorMessage);
        }
    }

    private static void join(Thread worker) throws InterruptedException {
        worker.join(WAIT_TIMEOUT.toMillis());

        if (worker.isAlive()) {
            worker.interrupt();
            worker.join(WAIT_TIMEOUT.toMillis());
        }

        if (worker.isAlive()) {
            throw new IllegalStateException(
                    "Worker không kết thúc đúng thời gian dự kiến: " + worker.getName()
            );
        }
    }

    private static void cleanup(Thread worker) throws InterruptedException {
        if (!worker.isAlive()) {
            return;
        }

        worker.interrupt();
        worker.join(WAIT_TIMEOUT.toMillis());

        if (worker.isAlive()) {
            throw new IllegalStateException(
                    "Worker vẫn còn sống sau cleanup: " + worker.getName()
            );
        }
    }

    private static void waitForState(Thread worker, Thread.State expectedState)
            throws InterruptedException {
        long deadline = System.nanoTime() + WAIT_TIMEOUT.toNanos();

        while (System.nanoTime() < deadline) {
            if (worker.getState() == expectedState) {
                return;
            }

            if (!worker.isAlive()) {
                break;
            }

            Thread.sleep(5);
        }

        throw new IllegalStateException(
                "Không quan sát được state "
                        + expectedState
                        + " của worker "
                        + worker.getName()
                        + ". State hiện tại: "
                        + worker.getState()
        );
    }

    private static final class StopSignal {
        private volatile boolean stopRequested;
    }
}
