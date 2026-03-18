package com.example.learning.module.synchronization.concurrent.service;

import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.*;

@Service
public class CopyOnWriteArrayService {
    public void copyOnWriteArrayWhenWritingExample() throws InterruptedException {
        int threadCount = 64;           // Số lượng luồng lớn để thấy rõ sự tranh chấp
        int totalElements = 100_000;    // Tổng số phần tử cần chèn
        int elementsPerThread = totalElements / threadCount;

        // 1. Synchronized List
        List syncList = Collections.synchronizedList(new ArrayList<>());

        // 2. ConcurrentSkipListMap
        List copyList = new CopyOnWriteArrayList<>();

        // 3. Async List
        List asyncList = new ArrayList<>();

        System.out.println("Cấu hình: " + threadCount + " luồng, mỗi luồng chèn " + elementsPerThread + " phần tử.");

        // TEST 1: Synchronized List
        long startSync = System.nanoTime();
        runBenchmark(syncList, threadCount, elementsPerThread);
        long endSync = System.nanoTime();
        double durationSync = (endSync - startSync) / 1_000_000.0;

        // TEST 2: Async List
        long startAsync = System.nanoTime();
        runBenchmark(asyncList, threadCount, elementsPerThread);
        long endAsync = System.nanoTime();
        double durationAsync = (endAsync - startAsync) / 1_000_000.0;

        // TEST 3: ConcurrentSkipListMap
        long startSkip = System.nanoTime();
        runBenchmark(copyList, threadCount, elementsPerThread);
        long endSkip = System.nanoTime();
        double durationSkip = (endSkip - startSkip) / 1_000_000.0;

        // KẾT QUẢ
        System.out.println("\n--- KẾT QUẢ TIME BENCHMARK ---");
        System.out.printf("1. Synchronized List : %.2f ms | Số request: %d\n",
                durationSync, syncList.size());

        System.out.printf("2. CopyOnWrite: %.2f ms | Số request: %d\n",
                durationSkip, copyList.size());

        System.out.printf("3. Async List        : %.2f ms | Số request: %d\n",
                durationAsync, asyncList.size());
    }

    public void copyOnWriteArrayWhenReadingExample() throws InterruptedException {
        int initialSize = 10000;

        // Tạo dữ liệu mẫu
        List<Integer> data = new ArrayList<>();
        for (int i = 0; i < initialSize; i++) data.add(i);

        // 1. ArrayList thường (Dễ sập)
        List<Integer> unsafeList = new ArrayList<>(data);

        // 2. CopyOnWriteArrayList (Bất tử)
        List<Integer> safeList = new CopyOnWriteArrayList<>(data);

        System.out.println("--- Bắt đầu Test với ArrayList (10,000 phần tử) ---");
        runStressTest(unsafeList);

        Thread.sleep(5000);

        System.out.println("\n--- Bắt đầu Test với CopyOnWriteArrayList (10,000 phần tử) ---");
        runStressTest(safeList);
    }

    private void runBenchmark(List list, int threadCount, int elementsPerThread) throws InterruptedException,NullPointerException {
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        for (int i = 0; i < threadCount; i++) {
            final int tId = i;
            executor.execute(() -> {
                try {
                    for (int j = 0; j < elementsPerThread; j++) {
                        int key = tId * elementsPerThread + j;
                        list.add(key);
                    }
                } catch (Exception e) {
                    System.err.println("Lỗi tại luồng " + tId + ": " + e.getMessage());
                }
            });
        }

        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);
    }

    private static void runStressTest(List<Integer> list) {
        // Luồng Đọc: Duyệt liên tục qua 10,000 phần tử
        Thread reader = new Thread(() -> {
            try {
                long count = 0;
                for (Integer i : list) {
                    count++;
                    // Giả lập xử lý nhẹ để kéo dài thời gian lặp
                    if (count % 1000 == 0) Thread.sleep(1);
                }
                System.out.println("Luồng Đọc hoàn tất an toàn. Tổng duyệt: " + count);
            } catch (Exception e) {
                System.err.println("LUỒNG ĐỌC BỊ SẬP: " + e.getClass().getSimpleName());
            }
        });

        // Luồng Ghi: Xóa phần tử ngẫu nhiên liên tục
        Thread writer = new Thread(() -> {
            try {
                Random rand = new Random();
                for (int i = 0; i < 100; i++) {
                    if (!list.isEmpty()) {
                        list.remove(rand.nextInt(list.size()));
                    }
                    Thread.sleep(5); // Xóa rải rác
                }
                System.out.println("Luồng Ghi hoàn tất 100 lần xóa.");
            } catch (Exception e) {
                System.err.println("LUỒNG GHI BỊ SẬP: " + e.getMessage());
            }
        });

        reader.start();
        writer.start();
    }
}
