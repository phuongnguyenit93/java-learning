package com.example.learning.module.pool.service;

import org.springframework.stereotype.Service;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Service
public class ThreadPoolExecutorService {
    public void fixedThreadPool() {
        // Tạo pool cố định 3 luồng
        ExecutorService fixedPool = Executors.newFixedThreadPool(3);

        for (int i = 1; i <= 10; i++) {
            int taskId = i;
            fixedPool.execute(() -> {
                System.out.println("Đang đọc file " + taskId + " bằng " + Thread.currentThread().getName());
                try { Thread.sleep(2000); } catch (InterruptedException e) {}
            });
        }
        // Kết quả: Luôn chỉ có 3 thread chạy, các task khác phải đợi trong Queue vô hạn.
    }

    public void cacheThreadPool() {
        // Pool tự co giãn, không giới hạn số lượng luồng tối đa
        ExecutorService cachedPool = Executors.newCachedThreadPool();

        for (int i = 1; i <= 5; i++) {
            int taskId = i;
            cachedPool.execute(() -> {
                System.out.println("Xử lý click chuột thứ " + taskId + " bằng " + Thread.currentThread().getName());
            });
        }
        // Kết quả: Nếu task đến nhanh, nó có thể tạo ra 5 thread khác nhau cùng lúc.
        // Sau 60s không dùng, các thread này sẽ tự bị tiêu hủy để tiết kiệm RAM.
    }

    public void singleThreadPool() {
        // Chỉ duy nhất 1 luồng
        ExecutorService singlePool = Executors.newSingleThreadExecutor();

        singlePool.execute(() -> System.out.println("Ghi log giao dịch 1 (Bắt đầu)"));
        singlePool.execute(() -> System.out.println("Ghi log giao dịch 2 (Tiếp theo)"));
        singlePool.execute(() -> System.out.println("Ghi log giao dịch 3 (Cuối cùng)"));

        // Kết quả: Chắc chắn Giao dịch 1 xong mới đến 2, rồi mới đến 3.
        // Không bao giờ có chuyện chạy song song.
    }

    public void scheduleThreadPool() {
        // Pool hỗ trợ lập lịch với 2 luồng
        ScheduledExecutorService scheduledPool = Executors.newScheduledThreadPool(2);

        System.out.println("Hệ thống bắt đầu chạy...");

        // Chạy sau 3 giây kể từ bây giờ (chạy 1 lần duy nhất)
        scheduledPool.schedule(() -> {
            System.out.println("--- Gửi email chào mừng khách hàng mới! ---");
        }, 3, TimeUnit.SECONDS);

        // Chạy định kỳ: Sau 1 giây bắt đầu, sau đó cứ mỗi 5 giây lại chạy lại
        scheduledPool.scheduleAtFixedRate(() -> {
            System.out.println("--- Kiểm tra trạng thái Server (Health Check) ---");
        }, 1, 5, TimeUnit.SECONDS);
    }
}
