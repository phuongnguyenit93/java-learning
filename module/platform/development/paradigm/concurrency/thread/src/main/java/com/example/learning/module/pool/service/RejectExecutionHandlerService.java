package com.example.learning.module.pool.service;

import org.springframework.stereotype.Service;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Service
public class RejectExecutionHandlerService {
    public void abortPolicy() throws InterruptedException {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                2, 2, 0, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(1), // Tổng sức chứa là 2 + 1 = 3
                new ThreadPoolExecutor.AbortPolicy());

        for (int i = 1; i <= 4; i++) {
            int taskId = i;
            try {
                executor.execute(() -> {
                    System.out.println("Đang xử lý Task " + taskId);
                    try { Thread.sleep(2000); } catch (InterruptedException e) {} // Giữ Thread thật lâu
                });
                System.out.println("Gửi Task " + taskId + " thành công.");
            } catch (RejectedExecutionException e) {
                System.err.println("--- Gửi Task " + taskId + " THẤT BẠI: Hệ thống quá tải! ---");
            }
            Thread.sleep(10);
        }
        executor.shutdown();
    }

    public void callerRunPolicy() throws InterruptedException {
        // Cấu hình: Max 2 Thread + 1 chỗ trong Queue = Sức chứa 3
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                2, 2, 0, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(1),
                new ThreadPoolExecutor.CallerRunsPolicy() // Chính sách "Tự làm đi"
        );

        System.out.println("--- Bắt đầu gửi 5 Task vào hệ thống ---");

        for (int i = 1; i <= 5; i++) {
            int taskId = i;
            System.out.println("Chuẩn bị gửi Task " + taskId + " từ luồng: " + Thread.currentThread().getName());

            executor.execute(() -> {
                String threadName = Thread.currentThread().getName();
                System.out.println(">>> Task " + taskId + " đang được xử lý bởi: " + threadName);
                try {
                    // Giả lập xử lý nặng trong 2 giây để làm đầy Pool
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                System.out.println("<<< Task " + taskId + " hoàn tất tại: " + threadName);
            });
            Thread.sleep(10);
        }

        executor.shutdown();
    }

    public void discardPolicy() {
        // Giả lập: Pool 1 thread, Queue 1 chỗ
        ThreadPoolExecutor executor = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(1), new ThreadPoolExecutor.DiscardPolicy());

        executor.execute(() -> {
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            System.out.println("Xong Task A"); }); // Chiếm Thread
        executor.execute(() -> System.out.println("Xong Task B")); // Chiếm Queue
        executor.execute(() -> System.out.println("Xong Task C")); // BỊ DISCARD (Mất tích)
    }

    public void discardOldestPolicy() {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(1), new ThreadPoolExecutor.DiscardOldestPolicy());

        executor.execute(() -> {
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            System.out.println("Xong Task A"); }); // Chiếm Thread
        executor.execute(() -> System.out.println("Xong Task B")); // Đang đợi trong Queue
        executor.execute(() -> System.out.println("Xong Task C")); // ĐUỔI TASK B, CHIẾM CHỖ TRONG QUEUE
    }
}
