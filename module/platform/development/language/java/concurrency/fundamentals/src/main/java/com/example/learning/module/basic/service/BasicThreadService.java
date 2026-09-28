package com.example.learning.module.basic.service;

import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class BasicThreadService {

    private static final Duration STATE_TIMEOUT = Duration.ofSeconds(2);

    public Map<String, Object> describeProcessAndCurrentThread() {
        Thread currentThread = Thread.currentThread();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("processId", ProcessHandle.current().pid());
        result.put("threadName", currentThread.getName());
        result.put("threadId", currentThread.threadId());
        result.put("threadState", currentThread.getState().name());
        result.put("daemon", currentThread.isDaemon());

        return result;
    }

    public List<String> compareRunAndStart() throws InterruptedException {
        String callerThreadName = Thread.currentThread().getName();

        Runnable directTask = () -> System.out.println(
                "run() trực tiếp đang chạy trên: "
                        + Thread.currentThread().getName()
        );

        directTask.run();

        Runnable startedTask = () -> System.out.println(
                "start() đang chạy task trên: "
                        + Thread.currentThread().getName()
        );

        Thread worker = new Thread(
                startedTask,
                "basic-start-worker"
        );

        try {
            worker.start();
            joinOrFail(worker);
        } finally {
            cleanup(worker);
        }

        return List.of(
                "Caller thread: " + callerThreadName,
                "Gọi run() trực tiếp -> task chạy trên caller thread: "
                        + callerThreadName,
                "Gọi start() -> task chạy trên worker thread: "
                        + worker.getName()
        );
    }

    public List<String> observeThreadLifecycle() throws InterruptedException {
        List<String> observations = new ArrayList<>();

        AtomicBoolean stayRunnable = new AtomicBoolean(true);
        CountDownLatch runnableEntered = new CountDownLatch(1);
        CountDownLatch timedWaitGate = new CountDownLatch(1);

        Object blockedMonitor = new Object();
        Object waitingMonitor = new Object();

        Thread worker = new Thread(() -> {
            runnableEntered.countDown();

            while (stayRunnable.get()) {
                Thread.onSpinWait();
            }

            try {
                timedWaitGate.await(30, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }

            synchronized (blockedMonitor) {
                // Chỉ cần lấy được monitor là đủ để rời trạng thái BLOCKED.
            }

            synchronized (waitingMonitor) {
                try {
                    waitingMonitor.wait();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }, "thread-state-worker");

        try {
            observations.add("NEW -> " + worker.getState());

            worker.start();

            if (!runnableEntered.await(STATE_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
                throw new IllegalStateException("Worker không bắt đầu đúng thời gian dự kiến.");
            }

            waitForState(worker, Thread.State.RUNNABLE);
            observations.add("RUNNABLE -> " + worker.getState());

            synchronized (blockedMonitor) {
                stayRunnable.set(false);

                waitForState(worker, Thread.State.TIMED_WAITING);
                observations.add("TIMED_WAITING -> " + worker.getState());

                timedWaitGate.countDown();

                waitForState(worker, Thread.State.BLOCKED);
                observations.add("BLOCKED -> " + worker.getState());
            }

            waitForState(worker, Thread.State.WAITING);
            observations.add("WAITING -> " + worker.getState());

            synchronized (waitingMonitor) {
                waitingMonitor.notifyAll();
            }

            worker.join(STATE_TIMEOUT.toMillis());

            if (worker.isAlive()) {
                throw new IllegalStateException("Worker không kết thúc đúng thời gian dự kiến.");
            }

            observations.add("TERMINATED -> " + worker.getState());

            return observations;
        } finally {
            stayRunnable.set(false);
            timedWaitGate.countDown();

            synchronized (waitingMonitor) {
                waitingMonitor.notifyAll();
            }

            if (worker.isAlive()) {
                worker.interrupt();
                worker.join(STATE_TIMEOUT.toMillis());
                if (worker.isAlive()) {
                    throw new IllegalStateException(
                            "Worker vẫn còn sống sau cleanup: " + worker.getName()
                    );
                }
            }
        }
    }

    private static void waitForState(
            Thread thread,
            Thread.State expectedState
    ) throws InterruptedException {
        long deadline = System.nanoTime() + STATE_TIMEOUT.toNanos();

        while (System.nanoTime() < deadline) {
            if (thread.getState() == expectedState) {
                return;
            }

            if (!thread.isAlive() && expectedState != Thread.State.TERMINATED) {
                break;
            }

            Thread.sleep(5);
        }

        throw new IllegalStateException(
                "Không quan sát được state "
                        + expectedState
                        + ". State hiện tại: "
                        + thread.getState()
        );
    }

    private static void joinOrFail(Thread worker) throws InterruptedException {
        worker.join(STATE_TIMEOUT.toMillis());
        if (worker.isAlive()) {
            throw new IllegalStateException("Worker không kết thúc đúng thời gian dự kiến: " + worker.getName());
        }
    }

    private static void cleanup(Thread worker) throws InterruptedException {
        if (!worker.isAlive()) return;
        worker.interrupt();
        worker.join(STATE_TIMEOUT.toMillis());
        if (worker.isAlive()) {
            throw new IllegalStateException("Worker vẫn còn sống sau cleanup: " + worker.getName());
        }
    }
}
