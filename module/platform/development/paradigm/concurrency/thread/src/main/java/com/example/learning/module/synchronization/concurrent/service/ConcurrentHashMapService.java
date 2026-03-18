package com.example.learning.module.synchronization.concurrent.service;

import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Service
public class ConcurrentHashMapService {
    public void concurrentHashMapExample() throws InterruptedException {
        int totalRequests = 2000;
        int threadCount = 20;

        // 1. Dùng ConcurrentHashMap (An toàn và Nhanh)
        Map<String, Integer> concurrentMap = new ConcurrentHashMap<>();

        // 2. Dùng HashMap thông thường (KHÔNG an toàn - Dễ mất dữ liệu)
        Map<String, Integer> unsafeHashMap = new HashMap<>();

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        for (int i = 0; i < totalRequests; i++) {
            executor.execute(() -> {
                // Xử lý với ConcurrentHashMap
                concurrentMap.merge("count", 1, Integer::sum);

                // Xử lý với HashMap thường (Cách viết này cực kỳ nguy hiểm trong đa luồng)
                Integer val = unsafeHashMap.get("count");
                unsafeHashMap.put("count", (val == null) ? 1 : val + 1);
            });
        }

        executor.shutdown();
        executor.awaitTermination(1, TimeUnit.MINUTES);

        System.out.println("--- KẾT QUẢ SO SÁNH ---");
        System.out.println("Tổng số request kỳ vọng: " + totalRequests);
        System.out.println("ConcurrentHashMap kết quả: " + concurrentMap.get("count"));
        System.out.println("HashMap thông thường kết quả: " + unsafeHashMap.get("count"));

        if (unsafeHashMap.get("count") < totalRequests) {
            System.out.println("\n=> CẢNH BÁO: HashMap đã bị mất dữ liệu do Race Condition!");
        }
    }
}
