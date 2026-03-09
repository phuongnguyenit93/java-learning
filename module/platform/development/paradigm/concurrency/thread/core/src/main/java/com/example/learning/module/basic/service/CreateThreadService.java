package com.example.learning.module.basic.service;

import com.example.learning.module.basic.thread.MyWorker;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.concurrent.*;

@Service
public class CreateThreadService {
    public void createByExtends() {
        MyWorker t1 = new MyWorker();
        t1.start();
    }

    public void createByRunnable() {
        // Cách 1: Dùng Anonymous Class
        Runnable task1 = new Runnable() {
            @Override
            public void run() {
                System.out.println("Runnable đang chạy...");
            }
        };

        // Cách 2: Dùng Lambda (Java 8+) - Rất gọn
        Runnable task2 = () -> System.out.println("Runnable Lambda đang chạy!");

        Thread t1 = new Thread(task1);
        Thread t2 = new Thread(task2);

        t1.start();
        t2.start();
    }

    public String createByCallable() throws ExecutionException, InterruptedException {
        Callable<String> task = () -> {
            Thread.sleep(2000);
            return "Kết quả từ luồng phụ!";
        };

        // Để chạy Callable, thường dùng ExecutorService
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<String> future = executor.submit(task);

        // Luồng chính có thể làm việc khác ở đây...

        // Lấy kết quả (Lệnh .get() sẽ đợi cho đến khi luồng phụ xong)
        return future.get();
    }

    // Tạo một pool cố định có 5 threads
    @Autowired
    @Qualifier("defaultTaskExecutor")
    Executor taskExecutor;

    public void createByThreadPool() {
        for (int i = 0; i < 5; i++) {
            int taskId = i;
            taskExecutor.execute(() -> {
                System.out.println("Task " + taskId + " đang được xử lý bởi " + Thread.currentThread().getName());
            });
        }
    }
}
