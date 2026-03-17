package com.example.learning.module.synchronization.atomic.service;

import org.springframework.stereotype.Service;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

@Service
public class AdderService {
    private static final int NUM_THREADS = 100; // Số luồng lớn để tạo tranh chấp cao
    private static final long TOTAL_INCREMENTS = 100_000_000L;
    private static final long INCREMENTS_PER_THREAD = TOTAL_INCREMENTS / NUM_THREADS;

    public void longAdderBenchmark() throws InterruptedException {
        System.out.println("Benchmark: " + NUM_THREADS + " threads, Total: " + TOTAL_INCREMENTS);
        System.out.println("--------------------------------------------------");

        // 1. Test AtomicLong
        AtomicLong atomicLong = new AtomicLong(0);
        long startAtomic = System.currentTimeMillis();
        runTask(() -> {
            for (long i = 0; i < INCREMENTS_PER_THREAD; i++) {
                atomicLong.incrementAndGet();
            }
        });
        long endAtomic = System.currentTimeMillis();
        System.out.printf("AtomicLong: %d ms | Kết quả: %d\n", (endAtomic - startAtomic), atomicLong.get());

        // 2. Test LongAdder
        LongAdder longAdder = new LongAdder();
        long startAdder = System.currentTimeMillis();
        runTask(() -> {
            for (long i = 0; i < INCREMENTS_PER_THREAD; i++) {
                longAdder.add(1);
            }
        });
        long endAdder = System.currentTimeMillis();
        System.out.printf("LongAdder:  %d ms | Kết quả: %d\n", (endAdder - startAdder), longAdder.sum());
    }

    private static void runTask(Runnable task) throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(NUM_THREADS);
        for (int i = 0; i < NUM_THREADS; i++) {
            executor.execute(task);
        }
        executor.shutdown();
        executor.awaitTermination(1, TimeUnit.MINUTES);
    }
}
