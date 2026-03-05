package com.example.learning.module.basic.service;

import org.springframework.stereotype.Service;

@Service
public class BasicThreadService {
    public void executeTaskWithThread() {
        // 1. Định nghĩa công việc (Runnable)
        Runnable runnableTask = () -> {
            try {
                System.out.println(">>> Luồng con bắt đầu xử lý: ");
                // Giả lập xử lý nặng tốn 5 giây
                Thread.sleep(5000);
                System.out.println("<<< Luồng con đã hoàn thành: ");
            } catch (InterruptedException e) {
                System.err.println("Luồng bị ngắt quãng!");
            }
        };

        // 2. Tạo luồng và chạy
        Thread thread = new Thread(runnableTask);
        thread.start();

        System.out.println("--- Phương thức đang chạy - Bạn có thể làm việc khác ---");
    }

    public void executeTaskWithNonThread() {

        System.out.println("Starting task - And you must wait here until done");
        try {
            Thread.sleep(5000);
        } catch (Exception e) {
            System.out.println("Lỗi");
        }

        System.out.println("--- Bạn đã chờ 5s và phương thức đã thực hiện xong ---");

    }

    public void executeThreadLifeCycle() throws InterruptedException {
        // 0. Tạo một đối tượng Lock để gây ra tranh chấp (BLOCKED)
        Object lock = new Object();

        // KHAI BÁO BIẾN Ở ĐÂY để dùng được ở mọi nơi trong hàm main
        Thread thread;
        // Luồng chính (Main) sẽ chiếm lock này trước để Thread con bị chặn
        synchronized (lock) {

            // 1. TRẠNG THÁI: NEW
            thread = new Thread(() -> {
                try {
                    // 3. TRẠNG THÁI: TIMED_WAITING
                    Thread.sleep(1000);

                    // 4. TRẠNG THÁI: BLOCKED
                    // Luồng con cố gắng vào đây nhưng Main đang giữ 'lock'
                    synchronized (lock) {
                        System.out.println("Luồng con đã lấy được lock!");
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            });

            System.out.println("1. Sau khi khởi tạo: " + thread.getState()); // NEW

            // 2. TRẠNG THÁI: RUNNABLE
            thread.start();
            System.out.println("2. Sau khi gọi start(): " + thread.getState()); // RUNNABLE

            // Chờ 0.5s để Thread con rơi vào sleep
            Thread.sleep(500);
            System.out.println("3. Khi đang sleep(): " + thread.getState()); // TIMED_WAITING

            // Chờ thêm 1s để Thread con thức dậy và cố gắng chiếm lock
            Thread.sleep(1000);
            System.out.println("4. Khi bị chặn bởi synchronized: " + thread.getState()); // BLOCKED

        } // <--- Đến đây Main mới nhả lock ra

        // 5. TRẠNG THÁI: TERMINATED
        thread.join();
        System.out.println("5. Sau khi hoàn thành: " + thread.getState()); // TERMINATED
    }


}
