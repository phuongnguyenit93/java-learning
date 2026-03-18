package com.example.learning.module.synchronization.atomic.service;

import org.springframework.stereotype.Service;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class AtomicService {
    private static final int NUM_THREADS = 10;
    private static final int INCREMENTS_PER_THREAD = 1_000_000;

    // Biến dùng cho Synchronized
    private int syncCount = 0;
    // Biến dùng cho Atomic
    private final AtomicInteger atomicCount = new AtomicInteger(0);

    public void atomicBenchMark() throws InterruptedException {
        System.out.println("Cấu hình: " + NUM_THREADS + " luồng, mỗi luồng tăng " + INCREMENTS_PER_THREAD + " lần.");
        System.out.println("--------------------------------------------------");

        // Test Synchronized
        long startSync = System.nanoTime();
        runSynchronized();
        long endSync = System.nanoTime();
        System.out.printf("Synchronized: %10.2f ms | Kết quả: %d\n",
                (endSync - startSync) / 1_000_000.0, syncCount);

        // Test Atomic
        long startAtomic = System.nanoTime();
        runAtomic();
        long endAtomic = System.nanoTime();
        System.out.printf("Atomic:       %10.2f ms | Kết quả: %d\n",
                (endAtomic - startAtomic) / 1_000_000.0, atomicCount.get());
    }

    private void runSynchronized() throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(NUM_THREADS);
        for (int i = 0; i < NUM_THREADS; i++) {
            executor.execute(() -> {
                for (int j = 0; j < INCREMENTS_PER_THREAD; j++) {
                    incrementSync();
                }
            });
        }
        executor.shutdown();
        executor.awaitTermination(1, TimeUnit.MINUTES);
    }

    private synchronized void incrementSync() {
        syncCount++;
    }

    private void runAtomic() throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(NUM_THREADS);
        for (int i = 0; i < NUM_THREADS; i++) {
            executor.execute(() -> {
                for (int j = 0; j < INCREMENTS_PER_THREAD; j++) {
                    atomicCount.getAndIncrement();
                }
            });
        }
        executor.shutdown();
        executor.awaitTermination(1, TimeUnit.MINUTES);
    }
}
