package com.example.learning.module.runtimediagnostics.service;

import org.springframework.stereotype.Service;

import java.lang.management.ClassLoadingMXBean;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.RuntimeMXBean;
import java.lang.management.ThreadMXBean;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class RuntimeDiagnosticsExperimentService {

    private static final int MIN_WINDOW_MILLIS = 2_000;
    private static final int MAX_WINDOW_MILLIS = 15_000;
    private static final int MIN_RETENTION_MEGABYTES = 1;
    private static final int MAX_RETENTION_MEGABYTES = 16;
    private static final int PAYLOAD_BYTES = 256 * 1024;

    private final AtomicLong experimentSequence =
            new AtomicLong();

    private final AtomicReference<ExperimentWindow> contentionWindow =
            new AtomicReference<>();

    private final AtomicReference<ExperimentWindow> cpuWindow =
            new AtomicReference<>();

    private final AtomicReference<HeapRetentionState> heapRetentionState =
            new AtomicReference<>();

    public Map<String, Object> startThreadContention(
            int requestedDurationMillis
    ) {
        int durationMillis =
                bound(
                        requestedDurationMillis,
                        MIN_WINDOW_MILLIS,
                        MAX_WINDOW_MILLIS
                );

        long experimentId =
                experimentSequence.incrementAndGet();

        String holderName =
                "runtime-diagnostics-lock-holder-" + experimentId;

        String waiterName =
                "runtime-diagnostics-lock-waiter-" + experimentId;

        ExperimentWindow window =
                new ExperimentWindow(
                        experimentId,
                        List.of(
                                holderName,
                                waiterName
                        )
                );

        ExperimentWindow active =
                contentionWindow.get();

        if (
                active != null ||
                        !contentionWindow.compareAndSet(
                                null,
                                window
                        )
        ) {
            return activeExperiment(
                    "thread-contention",
                    active != null
                            ? active
                            : contentionWindow.get()
            );
        }

        Object monitor =
                new Object();

        CountDownLatch holderAcquired =
                new CountDownLatch(1);

        Thread holder =
                Thread.ofPlatform()
                        .daemon(true)
                        .name(holderName)
                        .unstarted(() -> {
                            synchronized (monitor) {
                                window.markStarted(
                                        durationMillis
                                );
                                holderAcquired.countDown();
                                sleep(durationMillis);
                            }
                        });

        Thread waiter =
                Thread.ofPlatform()
                        .daemon(true)
                        .name(waiterName)
                        .unstarted(() -> {
                            try {
                                if (
                                        holderAcquired.await(
                                                1,
                                                TimeUnit.SECONDS
                                        )
                                ) {
                                    synchronized (monitor) {
                                        // Acquiring the monitor is the observation.
                                    }
                                }
                            } catch (InterruptedException exception) {
                                Thread.currentThread().interrupt();
                            }
                        });

        try {
            holder.start();

            if (
                    !holderAcquired.await(
                            1,
                            TimeUnit.SECONDS
                    )
            ) {
                interruptAndJoin(
                        holder,
                        waiter
                );

                contentionWindow.compareAndSet(
                        window,
                        null
                );

                return map(
                        "experiment", "thread-contention",
                        "started", false,
                        "reason", "holder-did-not-acquire-monitor"
                );
            }

            waiter.start();

            boolean waiterBlocked =
                    waitForState(
                    waiter,
                    Thread.State.BLOCKED,
                    750
            );

            if (!waiterBlocked) {
                interruptAndJoin(
                        holder,
                        waiter
                );

                contentionWindow.compareAndSet(
                        window,
                        null
                );

                return map(
                        "experiment", "thread-contention",
                        "started", false,
                        "reason", "waiter-did-not-enter-blocked-state"
                );
            }

            startCleanupCoordinator(
                    "runtime-diagnostics-contention-cleanup-" + experimentId,
                    window,
                    contentionWindow,
                    holder,
                    waiter
            );

            return map(
                    "experiment", "thread-contention",
                    "started", true,
                    "experimentId", experimentId,
                    "targetPid", ProcessHandle.current().pid(),
                    "effectiveDurationMillis", durationMillis,
                    "expiresAt",
                    expirationText(
                            window.expiresAtEpochMillis
                    ),
                    "holderThread", holderName,
                    "waiterThread", waiterName,
                    "holderStateAtResponse", holder.getState().name(),
                    "waiterStateAtResponse", waiter.getState().name(),
                    "suggestedCommand",
                    "jcmd " + ProcessHandle.current().pid() +
                            " Thread.print -l"
            );
        } catch (InterruptedException exception) {
            interruptAndJoin(
                    holder,
                    waiter
            );

            contentionWindow.compareAndSet(
                    window,
                    null
            );

            Thread.currentThread().interrupt();

            return map(
                    "experiment", "thread-contention",
                    "started", false,
                    "reason", "request-thread-interrupted"
            );
        } catch (RuntimeException | Error failure) {
            interruptAndJoin(
                    holder,
                    waiter
            );

            contentionWindow.compareAndSet(
                    window,
                    null
            );

            throw failure;
        }
    }

    public Map<String, Object> startHeapRetention(
            int requestedMegabytes,
            int requestedDurationMillis
    ) {
        int megabytes =
                bound(
                        requestedMegabytes,
                        MIN_RETENTION_MEGABYTES,
                        MAX_RETENTION_MEGABYTES
                );

        int durationMillis =
                bound(
                        requestedDurationMillis,
                        MIN_WINDOW_MILLIS,
                        MAX_WINDOW_MILLIS
                );

        long experimentId =
                experimentSequence.incrementAndGet();

        HeapRetentionState state =
                new HeapRetentionState(
                        experimentId
                );

        HeapRetentionState active =
                heapRetentionState.get();

        if (
                active != null ||
                        !heapRetentionState.compareAndSet(
                                null,
                                state
                        )
        ) {
            HeapRetentionState current =
                    active != null
                            ? active
                            : heapRetentionState.get();

            return map(
                    "experiment", "heap-retention",
                    "started", false,
                    "reason", "another-heap-retention-window-is-active",
                    "activeExperimentId",
                    current != null
                            ? current.experimentId
                            : null,
                    "activeExpiresAt",
                    current != null
                            ? expirationText(
                                    current.expiresAtEpochMillis
                            )
                            : null
            );
        }

        try {
            int payloadCount =
                    megabytes * 1024 * 1024 / PAYLOAD_BYTES;

            List<DiagnosticRetainedPayload> payloads =
                    new ArrayList<>(
                            payloadCount
                    );

            for (
                    int index = 0;
                    index < payloadCount;
                    index++
            ) {
                payloads.add(
                        new DiagnosticRetainedPayload(
                                index,
                                PAYLOAD_BYTES
                        )
                );
            }

            state.payloads =
                    List.copyOf(
                            payloads
                    );

            state.markStarted(
                    durationMillis
            );

            Thread.ofPlatform()
                    .daemon(true)
                    .name(
                            "runtime-diagnostics-heap-cleanup-" +
                                    experimentId
                    )
                    .start(() -> {
                        sleep(durationMillis);
                        state.payloads = List.of();
                        heapRetentionState.compareAndSet(
                                state,
                                null
                        );
                    });

            return map(
                    "experiment", "heap-retention",
                    "started", true,
                    "experimentId", experimentId,
                    "targetPid", ProcessHandle.current().pid(),
                    "requestedMegabytes", requestedMegabytes,
                    "effectiveMegabytes", megabytes,
                    "payloadClass",
                    DiagnosticRetainedPayload.class.getName(),
                    "payloadCount", payloadCount,
                    "retainedBytes",
                    (long) payloadCount * PAYLOAD_BYTES,
                    "effectiveDurationMillis", durationMillis,
                    "expiresAt",
                    expirationText(
                            state.expiresAtEpochMillis
                    ),
                    "suggestedCommand",
                    "jcmd " + ProcessHandle.current().pid() +
                            " GC.class_histogram"
            );
        } catch (RuntimeException | Error failure) {
            state.payloads = List.of();
            heapRetentionState.compareAndSet(
                    state,
                    null
            );
            throw failure;
        }
    }

    public Map<String, Object> startCpuWindow(
            int requestedDurationMillis
    ) {
        int durationMillis =
                bound(
                        requestedDurationMillis,
                        MIN_WINDOW_MILLIS,
                        MAX_WINDOW_MILLIS
                );

        long experimentId =
                experimentSequence.incrementAndGet();

        String workerName =
                "runtime-diagnostics-cpu-worker-" + experimentId;

        ExperimentWindow window =
                new ExperimentWindow(
                        experimentId,
                        List.of(
                                workerName
                        )
                );

        ExperimentWindow active =
                cpuWindow.get();

        if (
                active != null ||
                        !cpuWindow.compareAndSet(
                                null,
                                window
                        )
        ) {
            return activeExperiment(
                    "jfr-cpu-window",
                    active != null
                            ? active
                            : cpuWindow.get()
            );
        }

        CountDownLatch workerStarted =
                new CountDownLatch(1);

        Thread worker =
                Thread.ofPlatform()
                        .daemon(true)
                        .name(workerName)
                        .unstarted(() -> {
                            window.markStarted(
                                    durationMillis
                            );
                            workerStarted.countDown();

                            try {
                                runCpuLoop(
                                        durationMillis
                                );
                            } finally {
                                cpuWindow.compareAndSet(
                                        window,
                                        null
                                );
                            }
                        });

        try {
            worker.start();
            if (
                    !workerStarted.await(
                            1,
                            TimeUnit.SECONDS
                    )
            ) {
                interruptAndJoin(
                        worker
                );
                cpuWindow.compareAndSet(
                        window,
                        null
                );
                return map(
                        "experiment", "jfr-cpu-window",
                        "started", false,
                        "reason", "worker-did-not-start"
                );
            }
        } catch (InterruptedException exception) {
            interruptAndJoin(
                    worker
            );
            cpuWindow.compareAndSet(
                    window,
                    null
            );
            Thread.currentThread().interrupt();
            return map(
                    "experiment", "jfr-cpu-window",
                    "started", false,
                    "reason", "request-thread-interrupted"
            );
        } catch (RuntimeException | Error failure) {
            cpuWindow.compareAndSet(
                    window,
                    null
            );
            throw failure;
        }

        return map(
                "experiment", "jfr-cpu-window",
                "started", true,
                "experimentId", experimentId,
                "targetPid", ProcessHandle.current().pid(),
                "effectiveDurationMillis", durationMillis,
                "expiresAt",
                expirationText(
                        window.expiresAtEpochMillis
                ),
                "workerThread", workerName,
                "workerStateAtResponse", worker.getState().name(),
                "observation",
                "Record this window with JFR and inspect execution samples."
        );
    }

    public Map<String, Object> managementSnapshot() {
        RuntimeMXBean runtime =
                ManagementFactory.getRuntimeMXBean();

        MemoryMXBean memory =
                ManagementFactory.getMemoryMXBean();

        ThreadMXBean threads =
                ManagementFactory.getThreadMXBean();

        ClassLoadingMXBean classes =
                ManagementFactory.getClassLoadingMXBean();

        OperatingSystemMXBean os =
                ManagementFactory.getOperatingSystemMXBean();

        Map<String, Object> result =
                map(
                        "sampledAt", Instant.now().toString(),
                        "pid", ProcessHandle.current().pid(),
                        "runtime",
                        map(
                                "vmName", runtime.getVmName(),
                                "vmVendor", runtime.getVmVendor(),
                                "vmVersion", runtime.getVmVersion(),
                                "uptimeMillis", runtime.getUptime()
                        ),
                        "heap",
                        memoryUsage(
                                memory.getHeapMemoryUsage()
                        ),
                        "nonHeap",
                        memoryUsage(
                                memory.getNonHeapMemoryUsage()
                        ),
                        "platformThreads",
                        map(
                                "current", threads.getThreadCount(),
                                "peak", threads.getPeakThreadCount(),
                                "daemon", threads.getDaemonThreadCount(),
                                "totalStarted",
                                threads.getTotalStartedThreadCount()
                        ),
                        "classes",
                        map(
                                "currentlyLoaded",
                                classes.getLoadedClassCount(),
                                "totalLoaded",
                                classes.getTotalLoadedClassCount(),
                                "totalUnloaded",
                                classes.getUnloadedClassCount()
                        ),
                        "garbageCollectors",
                        garbageCollectors(),
                        "operatingSystem",
                        map(
                                "name", os.getName(),
                                "version", os.getVersion(),
                                "architecture", os.getArch(),
                                "availableProcessors",
                                os.getAvailableProcessors(),
                                "systemLoadAverage",
                                os.getSystemLoadAverage()
                        )
                );

        if (
                os instanceof
                        com.sun.management.OperatingSystemMXBean extended
        ) {
            result.put(
                    "jdkOperatingSystemExtension",
                    map(
                            "available", true,
                            "processCpuTimeNanos",
                            extended.getProcessCpuTime(),
                            "processCpuLoad",
                            extended.getProcessCpuLoad(),
                            "systemCpuLoad",
                            extended.getCpuLoad(),
                            "committedVirtualMemoryBytes",
                            extended.getCommittedVirtualMemorySize(),
                            "totalMemoryBytes",
                            extended.getTotalMemorySize(),
                            "freeMemoryBytes",
                            extended.getFreeMemorySize()
                    )
            );
        } else {
            result.put(
                    "jdkOperatingSystemExtension",
                    map(
                            "available",
                            false
                    )
            );
        }

        return result;
    }

    private void runCpuLoop(
            int durationMillis
    ) {
        long deadline =
                System.nanoTime() +
                        TimeUnit.MILLISECONDS.toNanos(
                                durationMillis
                        );

        long value =
                0x9E3779B97F4A7C15L;

        long iterations =
                0;

        while (
                System.nanoTime() <
                        deadline
        ) {
            value ^= value << 13;
            value ^= value >>> 7;
            value ^= value << 17;
            iterations++;
        }

        CPU_SINK =
                value ^ iterations;
    }

    private static volatile long CPU_SINK;

    private static Map<String, Object> memoryUsage(
            MemoryUsage usage
    ) {
        return map(
                "initBytes", usage.getInit(),
                "usedBytes", usage.getUsed(),
                "committedBytes", usage.getCommitted(),
                "maxBytes", usage.getMax()
        );
    }

    private static List<Map<String, Object>> garbageCollectors() {
        return ManagementFactory
                .getGarbageCollectorMXBeans()
                .stream()
                .map(bean ->
                        map(
                                "name", bean.getName(),
                                "collectionCount",
                                bean.getCollectionCount(),
                                "collectionTimeMillis",
                                bean.getCollectionTime(),
                                "memoryPools",
                                List.of(
                                        bean.getMemoryPoolNames()
                                )
                        )
                )
                .toList();
    }

    private static <T> void startCleanupCoordinator(
            String threadName,
            T expectedState,
            AtomicReference<T> stateReference,
            Thread... workers
    ) {
        Thread.ofPlatform()
                .daemon(true)
                .name(threadName)
                .start(() -> {
                    for (Thread worker : workers) {
                        join(worker);
                    }

                    stateReference.compareAndSet(
                            expectedState,
                            null
                    );
                });
    }

    private static boolean waitForState(
            Thread thread,
            Thread.State expectedState,
            long timeoutMillis
    ) {
        long deadline =
                System.nanoTime() +
                        TimeUnit.MILLISECONDS.toNanos(
                                timeoutMillis
                        );

        while (
                thread.isAlive() &&
                        thread.getState() != expectedState &&
                        System.nanoTime() < deadline
        ) {
            try {
                Thread.sleep(10);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return false;
            }
        }

        return thread.getState() == expectedState;
    }

    private static void interruptAndJoin(
            Thread... workers
    ) {
        for (Thread worker : workers) {
            if (worker.isAlive()) {
                worker.interrupt();
            }
        }

        for (Thread worker : workers) {
            join(worker);
        }
    }

    private static void join(
            Thread thread
    ) {
        boolean interrupted =
                false;

        while (thread.isAlive()) {
            try {
                thread.join();
            } catch (InterruptedException exception) {
                interrupted = true;
            }
        }

        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private static void sleep(
            long millis
    ) {
        try {
            Thread.sleep(
                    millis
            );
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private static int bound(
            int value,
            int minimum,
            int maximum
    ) {
        return Math.max(
                minimum,
                Math.min(
                        maximum,
                        value
                )
        );
    }

    private static Map<String, Object> activeExperiment(
            String experiment,
            ExperimentWindow active
    ) {
        return map(
                "experiment", experiment,
                "started", false,
                "reason", "another-window-is-active",
                "activeExperimentId",
                active != null
                        ? active.experimentId
                        : null,
                "activeExpiresAt",
                active != null
                        ? expirationText(
                                active.expiresAtEpochMillis
                        )
                        : null,
                "activeThreads",
                active != null
                        ? active.threadNames
                        : List.of()
        );
    }

    private static Map<String, Object> map(
            Object... pairs
    ) {
        Map<String, Object> result =
                new LinkedHashMap<>();

        for (
                int index = 0;
                index < pairs.length;
                index += 2
        ) {
            result.put(
                    (String) pairs[index],
                    pairs[index + 1]
            );
        }

        return result;
    }

    private static String expirationText(
            long expiresAtEpochMillis
    ) {
        return expiresAtEpochMillis > 0
                ? Instant.ofEpochMilli(
                        expiresAtEpochMillis
                ).toString()
                : null;
    }

    private static final class ExperimentWindow {

        private final long experimentId;
        private final List<String> threadNames;
        private volatile long expiresAtEpochMillis;

        private ExperimentWindow(
                long experimentId,
                List<String> threadNames
        ) {
            this.experimentId =
                    experimentId;
            this.threadNames =
                    threadNames;
        }

        private void markStarted(
                int durationMillis
        ) {
            expiresAtEpochMillis =
                    System.currentTimeMillis() +
                            durationMillis;
        }
    }

    private static final class HeapRetentionState {

        private final long experimentId;
        private volatile long expiresAtEpochMillis;
        private volatile List<DiagnosticRetainedPayload> payloads =
                List.of();

        private HeapRetentionState(
                long experimentId
        ) {
            this.experimentId =
                    experimentId;
        }

        private void markStarted(
                int durationMillis
        ) {
            expiresAtEpochMillis =
                    System.currentTimeMillis() +
                            durationMillis;
        }
    }

    private static final class DiagnosticRetainedPayload {

        private final int sequence;
        private final byte[] bytes;

        private DiagnosticRetainedPayload(
                int sequence,
                int byteCount
        ) {
            this.sequence =
                    sequence;
            this.bytes =
                    new byte[byteCount];

            for (
                    int offset = 0;
                    offset < bytes.length;
                    offset += 4_096
            ) {
                bytes[offset] =
                        (byte) (
                                sequence +
                                        offset
                        );
            }
        }
    }
}
