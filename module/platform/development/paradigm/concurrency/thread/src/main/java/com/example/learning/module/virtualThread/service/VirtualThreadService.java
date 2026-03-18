package com.example.learning.module.virtualThread.service;

import org.springframework.stereotype.Service;
import java.util.concurrent.Executors;
import java.time.Duration;
import java.util.concurrent.Semaphore;

@Service
public class VirtualThreadService {
    public void virtualThreadBenchmark() {
        int TASK_COUNT = 1000;
        // --- TEST 1: Platform Threads (Luồng truyền thống) ---
        // Cảnh báo: Chỗ này có thể làm treo máy nếu không có Pool giới hạn
        System.out.println("Bắt đầu với Platform Threads...");
        long start1 = System.currentTimeMillis();
        try (var executor = Executors.newFixedThreadPool(100)) { // Chỉ dám tạo pool 100
            for (int i = 0; i < TASK_COUNT; i++) {
                executor.submit(() -> {
                    Thread.sleep(Duration.ofSeconds(1));
                    return null;
                });
            }
        }
        System.out.println("Platform Threads hoàn thành: " + (System.currentTimeMillis() - start1) + "ms");

        // --- TEST 2: Virtual Threads (Java 21+) ---
        System.out.println("\nBắt đầu với Virtual Threads...");
        long start2 = System.currentTimeMillis();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < TASK_COUNT; i++) {
                executor.submit(() -> {
                    Thread.sleep(Duration.ofSeconds(1));
                    return null;
                });
            }
        }
        System.out.println("Virtual Threads hoàn thành: " + (System.currentTimeMillis() - start2) + "ms");
    }

    public void virtualThreadBasicExample() {
        Thread.startVirtualThread(() -> {
            System.out.println("Đang chạy trong luồng ảo: " + Thread.currentThread());
        });
    }

    public void virtualThreadBuilderExample() {
        Thread.ofVirtual()
                .name("batch-worker-", 1)
                .start(() -> {
                    System.out.println("Working with Builder Virtual Thread");
                });
    }

    public void virtualThreadExecutorExample() {
        final Semaphore SEMAPHORE = new Semaphore(100);
        System.out.println("Bắt đầu xử lý data");
        long start = System.currentTimeMillis();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < 300; i++) {
                executor.submit(() -> {
                    try {
                        SEMAPHORE.acquire(); // Xin phép đi qua barie
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        SEMAPHORE.release(); // Trả lại phép cho người khác
                    }
                });
            }
        }
        long end = System.currentTimeMillis();

        System.out.println("Handle data hoàn tất trong : " + (end - start) + "ms");

    }

}
