package com.example.learning.module.synchronization.concurrent.service;

import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.TreeMap;
import java.util.Collections;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class ConcurrentSkipListService {
    public void concurrentSkipListExample() throws InterruptedException {
        int threadCount = 64;           // Số lượng luồng lớn để thấy rõ sự tranh chấp
        int totalElements = 100_000;    // Tổng số phần tử cần chèn
        int elementsPerThread = totalElements / threadCount;

        // 1. Synchronized TreeMap
        Map<Integer, String> syncTreeMap = Collections.synchronizedMap(new TreeMap<>());

        // 2. ConcurrentSkipListMap
        Map<Integer, String> skipListMap = new ConcurrentSkipListMap<>();

        // 3. Async TreeMap
        Map<Integer, String> asyncTreeMap = new TreeMap<>();

        System.out.println("Cấu hình: " + threadCount + " luồng, mỗi luồng chèn " + elementsPerThread + " phần tử.");

        // TEST 1: Synchronized TreeMap
        long startSync = System.nanoTime();
        runBenchmark(syncTreeMap, threadCount, elementsPerThread);
        long endSync = System.nanoTime();
        double durationSync = (endSync - startSync) / 1_000_000.0;

        long startAsync = System.nanoTime();
        runBenchmark(asyncTreeMap, threadCount, elementsPerThread);
        long endAsync = System.nanoTime();
        double durationAsync = (endAsync - startAsync) / 1_000_000.0;

        // TEST 2: ConcurrentSkipListMap
        long startSkip = System.nanoTime();
        runBenchmark(skipListMap, threadCount, elementsPerThread);
        long endSkip = System.nanoTime();
        double durationSkip = (endSkip - startSkip) / 1_000_000.0;

        // KẾT QUẢ
        System.out.println("\n--- KẾT QUẢ TIME BENCHMARK ---");
        System.out.printf("1. Synchronized TreeMap : %.2f ms | Số request: %d\n",
                durationSync, syncTreeMap.size());

        System.out.printf("2. ConcurrentSkipListMap: %.2f ms | Số request: %d\n",
                durationSkip, skipListMap.size());

        System.out.printf("3. Async TreeMap        : %.2f ms | Số request: %d\n",
                durationAsync, asyncTreeMap.size());
    }

    private void runBenchmark(Map<Integer, String> map, int threadCount, int elementsPerThread) throws InterruptedException,NullPointerException {
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        for (int i = 0; i < threadCount; i++) {
            final int tId = i;
            executor.execute(() -> {
                try {
                    for (int j = 0; j < elementsPerThread; j++) {
                        int key = tId * elementsPerThread + j;
                        map.put(key, "Val" + key);
                    }
                } catch (Exception e) {
                    System.err.println("Lỗi tại luồng " + tId + ": " + e.getMessage());
                }
            });
        }

        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);
    }
}
