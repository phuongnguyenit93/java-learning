package com.example.learning.module.interruption.service;

import com.example.learning.config.thread.ThreadPoolConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

@Service
public class InterruptionService {
    @Autowired
    @Qualifier("interruptTaskExecutor")
    Executor taskExecutor;

    /**
     * Ví dụ về cách ngắt một luồng đang làm việc (Busy Loop).
     * Luồng này không nghỉ (sleep), nên nó phải chủ động kiểm tra cờ isInterrupted().
     */
    public void workerInterruption() throws InterruptedException {
        Thread worker = new Thread(() -> {
            long count = 0;
            System.out.println("Worker: Bắt đầu xử lý dữ liệu nặng...");

            // KIẾN THỨC: Phải chủ động kiểm tra cờ hiệu để dừng lại
            while (!Thread.currentThread().isInterrupted()) {
                count++;
                // Giả lập logic tính toán nặng
                if (count % 1_000_000_000 == 0) {
                    System.out.println("Worker: Đã xử lý được " + count + " bản ghi...");
                }
            }

            System.out.println("Worker: Nhận tín hiệu ngắt! Tổng bản ghi đã xử lý: " + count);
            System.out.println("Worker: Đang giải phóng bộ nhớ và kết thúc...");
        });

        worker.start();

        // Cho worker chạy 2 giây rồi ngắt
        Thread.sleep(2000);
        System.out.println("Main: Hết thời gian chờ, yêu cầu Worker dừng lại.");
        worker.interrupt();
    }

    /**
     * Ví dụ về xử lý InterruptedException.
     * Khi luồng đang sleep/wait, interrupt() sẽ ném ra ngoại lệ và XÓA cờ ngắt.
     */
    public void blockingInterruption() throws InterruptedException {
        Thread sleeper = new Thread(() -> {
            try {
                System.out.println("Sleeper: Tôi bắt đầu ngủ 10 giây...");
                Thread.sleep(10000);
            } catch (InterruptedException e) {
                // KIẾN THỨC: Tại đây cờ ngắt đã bị reset về false
                System.err.println("Sleeper: !!! Bị đánh thức đột ngột bằng ngoại lệ.");

                // THỰC HÀNH TỐT: Khôi phục lại trạng thái ngắt cho các tầng code phía trên (nếu có)
                Thread.currentThread().interrupt();
            }

            if (Thread.currentThread().isInterrupted()) {
                System.out.println("Sleeper: Kiểm tra lại thấy cờ ngắt là true. Kết thúc an toàn.");
            }
        });

        sleeper.start();

        Thread.sleep(1500);
        System.out.println("Main: Gửi lệnh ngắt cho Sleeper.");
        sleeper.interrupt();
    }

    /**
     * Cách đóng một Thread Pool sạch sẽ (Graceful Shutdown).
     * Kết hợp shutdown() và awaitTermination() cùng với interrupt().
     * hực tế (shutdownNow): Trong các thư viện lớn như Spring, khi một ứng dụng tắt, nó sẽ gọi shutdownNow() – thực chất là gửi interrupt() hàng loạt đến các luồng trong Pool.
     */
    public void gracefulShutdownDemoByJava() throws InterruptedException {
        ThreadPoolTaskExecutor springExecutor = (ThreadPoolTaskExecutor) taskExecutor;

        // Gửi 5 task vào pool
        for (int i = 1; i <= 5; i++) {
            int taskId = i;
            taskExecutor.execute(() -> {
                try {
                    System.out.println("Task " + taskId + " đang chạy...");
                    Thread.sleep(5000);
                    System.out.println("Task " + taskId + " hoàn thành.");
                } catch (InterruptedException e) {
                    System.err.println("Task " + taskId + " bị ngắt giữa chừng!");
                    Thread.currentThread().interrupt();
                }
            });
        }

        // Lấy "lõi" Java ra để điều khiển thủ công
        var nativeExecutor = springExecutor.getThreadPoolExecutor();
        // BẮT ĐẦU ĐÓNG POOL
        nativeExecutor.shutdown(); // Không nhận task mới, nhưng đợi task cũ chạy nốt
        System.out.println("Main: Đã gọi shutdown, đang đợi task hoàn thành...");

        try {
            // Đợi tối đa 3 giây cho các task chạy xong
            if (!nativeExecutor.awaitTermination(3, TimeUnit.SECONDS)) {
                System.out.println("Main: Hết thời gian chờ, buộc dừng các task còn lại!");
                nativeExecutor.shutdownNow(); // Gửi interrupt() đến tất cả các luồng đang chạy
            }
        } catch (InterruptedException e) {
            nativeExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }

        System.out.println("Main: Hệ thống đã đóng cửa sạch sẽ.");
    }

    public void gracefulShutdownDemoBySpring() throws InterruptedException {
        ThreadPoolTaskExecutor springExecutor = (ThreadPoolTaskExecutor) taskExecutor;

        // Gửi 5 task vào pool
        for (int i = 1; i <= 5; i++) {
            int taskId = i;
            taskExecutor.execute(() -> {
                try {
                    System.out.println("Task " + taskId + " đang chạy...");
                    Thread.sleep(10000);
                    System.out.println("Task " + taskId + " hoàn thành.");
                } catch (InterruptedException e) {
                    System.err.println("Task " + taskId + " bị ngắt giữa chừng!");
                    Thread.currentThread().interrupt();
                }
            });
        }

        // BẮT ĐẦU ĐÓNG POOL
        System.out.println("Main: Đang yêu cầu đóng Spring Pool...");

        // Trong Spring, destroy() tương đương với shutdown() và đợi dọn dẹp
        //executor.setWaitForTasksToCompleteOnShutdown(true);
        //executor.setAwaitTerminationSeconds(3);
        springExecutor.destroy();

        System.out.println("Main: Hệ thống đã đóng cửa.");
    }

    public void interruptedDemo() throws InterruptedException {
        Thread worker = new Thread(() -> {
            // 1. Giả lập luồng bị ngắt từ bên ngoài
            while (!Thread.currentThread().isInterrupted()) {
                // Đang làm việc...
            }

            System.out.println("--- Giai đoạn kiểm tra ---");

            // 2. Kiểm tra bằng isInterrupted() (KHÔNG XÓA CỜ)
            // Gọi bao nhiêu lần cũng vẫn ra true
            System.out.println("Lần 1 - isInterrupted(): " + Thread.currentThread().isInterrupted()); // true
            System.out.println("Lần 2 - isInterrupted(): " + Thread.currentThread().isInterrupted()); // true

            System.out.println("--- Giai đoạn xóa cờ ---");

            // 3. Kiểm tra bằng Thread.interrupted() (CÓ XÓA CỜ)
            // Lần đầu gọi sẽ trả về true và đặt lại cờ về false
            System.out.println("Lần 1 - interrupted(): " + Thread.interrupted()); // true

            // Lần thứ hai gọi, vì cờ đã bị xóa ở trên nên sẽ trả về false
            System.out.println("Lần 2 - interrupted(): " + Thread.interrupted()); // false

            // Kiểm tra lại bằng isInterrupted() để xác nhận cờ đã mất
            System.out.println("Kiểm tra lại isInterrupted(): " + Thread.currentThread().isInterrupted()); // false
        });

        worker.start();
        Thread.sleep(1000);

        System.out.println("Main: Gọi interrupt()...");
        worker.interrupt(); // Người gửi: Đặt cờ thành true
    }
}
