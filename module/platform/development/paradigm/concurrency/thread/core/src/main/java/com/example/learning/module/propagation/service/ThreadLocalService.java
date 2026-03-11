package com.example.learning.module.propagation.service;

import org.springframework.stereotype.Service;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class ThreadLocalService {
    // Khai báo ThreadLocal để lưu Transaction ID
    private static final ThreadLocal<String> transactionId = new ThreadLocal<>();

    // Dùng InheritableThreadLocal thay vì ThreadLocal
    private static final InheritableThreadLocal<String> parentContext = new InheritableThreadLocal<>();

    private static final InheritableThreadLocal<String> context = new InheritableThreadLocal<>();

    public void threadLocalDemo() {
        Runnable task = () -> {
            // Set giá trị dựa trên tên Thread
            String id = "TXN-" + Thread.currentThread().getName();
            transactionId.set(id);

            System.out.println(Thread.currentThread().getName() + " đã thiết lập: " + transactionId.get());

            // Giả lập xử lý logic...
            System.out.println("Doing some logic ");

            // CỰC KỲ QUAN TRỌNG: Xóa dữ liệu sau khi xong việc
            transactionId.remove();
        };

        Thread t1 = new Thread(task, "Thread-1");
        Thread t2 = new Thread(task, "Thread-2");

        t1.start();
        t2.start();
    }

    public void inheritableThreadLocalDemo() throws InterruptedException {
        // Thread cha thiết lập dữ liệu
        parentContext.set("SECRET_TOKEN_123");
        System.out.println("Cha (Main Thread) đang giữ: " + parentContext.get());

        Thread childThread = new Thread(() -> {
            // Thread con tự động có dữ liệu này
            System.out.println("Con (Child Thread) thừa kế được: " + parentContext.get());

            // Thử thay đổi ở con
            parentContext.set("CHILD_NEW_TOKEN");
            System.out.println("Con sau khi đổi: " + parentContext.get());
        });

        childThread.start();
        childThread.join();

        // Kiểm tra xem cha có bị ảnh hưởng bởi thay đổi của con không
        System.out.println("Cha kiểm tra lại: " + parentContext.get());
        parentContext.remove();
        // Kết quả vẫn là SECRET_TOKEN_123 (Vì đây là bản copy khi khởi tạo)
    }

    public void threadPoolIssueDemo () throws InterruptedException {
        // Tạo Pool chỉ có 1 Thread duy nhất để dễ thấy lỗi
        ExecutorService pool = Executors.newFixedThreadPool(1);

        // Lần 1: Set context là "USER_A"
        context.set("USER_A");
        pool.submit(() -> {
            System.out.println("Task 1 (Mong đợi USER_A): " + context.get());
        });

        Thread.sleep(1000);

        // Lần 2: Set context là "USER_B" ở Thread chính
        context.set("USER_B");
        pool.submit(() -> {
            // LỖI Ở ĐÂY: Nó vẫn sẽ in ra USER_A vì Thread-1 không được tạo mới
            System.out.println("Task 2 (Mong đợi USER_B): " + context.get());
        });

        pool.shutdown();
    }
}
