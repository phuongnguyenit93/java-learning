package com.example.learning.module.basic.service;

import org.springframework.stereotype.Service;

@Service
public class DaemonThreadService {
    private volatile boolean keepRunning = true;

    public void executeDaemonThread() {
        Thread daemonWorker = new Thread(() -> {
            try {
                while (keepRunning) {
                    System.out.println("Daemon đang dọn dẹp hệ thống...");
                    Thread.sleep(1000);
                }
            } catch (InterruptedException e) {
                System.out.println("Daemon bị interrupt!");
            } finally {
                // KHÚC NÀY CHO BIẾT THREAD ĐÃ NGỪNG
                System.out.println(">>> THÔNG BÁO: Daemon Thread đã thoát hoàn toàn.");
            }
        });

        // Thiết lập là Daemon
        daemonWorker.setDaemon(true);
        daemonWorker.start();

        System.out.println("Luồng chính (Main) hoàn thành công việc và thoát.");
        // Khi Main thoát, daemonWorker sẽ bị đóng ngay lập tức
        // dù nó đang ở trong vòng lặp vô tận.
    }

    public void stopDaemon() {
        this.keepRunning = false; // Đổi cờ để dừng vòng lặp trong Thread
        System.out.println("Daemon đã stop");
    }

    public void restartDaemon() {
        this.keepRunning = true; // Đổi cờ để dừng vòng lặp trong Thread
        System.out.println("Daemon đã restart");
    }
}
