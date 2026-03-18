package com.example.learning.module.synchronization.concurrent.service;

import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class ConcurrentLinkedQueueService {
    public void concurrentLinkedQueueExample() throws InterruptedException {
        // Hàng đợi phi chặn, an toàn đa luồng
        ConcurrentLinkedQueue<String> logQueue = new ConcurrentLinkedQueue<>();

        // Giả lập nhiều luồng đẩy log vào (Producers)
        ExecutorService producerService = Executors.newFixedThreadPool(5);
        for (int i = 0; i < 5; i++) {
            final int producerId = i;
            producerService.execute(() -> {
                for (int j = 0; j < 100; j++) {
                    logQueue.offer("Producer " + producerId + " - Log #" + j);
                }
            });
        }

        // Luồng lấy log ra xử lý (Consumer)
        Thread consumer = new Thread(() -> {
            while (true) {
                String log = logQueue.poll(); // Lấy ra và xóa khỏi hàng đợi
                if (log != null) {
                    // Xử lý ghi log vào file hoặc DB
                    System.out.println("Processing: " + log);
                }
            }
        });
        consumer.setDaemon(true); // Để luồng này tự tắt khi main tắt
        consumer.start();

        producerService.shutdown();
        Thread.sleep(1000);
        System.out.println("Số lượng log còn dư: " + logQueue.size());
    }
}
