package com.example.learning.module.synchronization.lock.service;

import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.StampedLock;

@Service
public class StampedLockService {
    private double goldPrice = 85.50; // Triệu đồng/lượng
    private final StampedLock sl = new StampedLock();

    public void stampLockSample() {

        // Luồng người dùng A đang xem giá
        Thread userA = new Thread(() -> viewPrice("Người dùng A"), "User-A");

        // Luồng Admin cập nhật giá xen ngang
        Thread admin = new Thread(() -> updatePrice(89.20), "Admin");

        userA.start();
        try {
            Thread.sleep(50); // Admin nhảy vào ngay sau khi User A bắt đầu xem
            admin.start();

            userA.join();
            admin.join();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    // 1. Dành cho hàng triệu người dùng xem giá (Optimistic Read)
    private void viewPrice(String userName) {
        // Lấy stamp lạc quan - KHÔNG giữ khóa, KHÔNG chặn ai cả
        long stamp = sl.tryOptimisticRead();

        double currentPrice = goldPrice;

        // Giả lập độ trễ mạng hoặc xử lý dữ liệu (200ms)
        try { TimeUnit.MILLISECONDS.sleep(2000); } catch (InterruptedException e) {}

        // Kiểm tra xem trong 200ms qua, giá vàng có bị thay đổi không?
        if (!sl.validate(stamp)) {
            System.err.println("(!) " + userName + ": Giá vàng vừa cập nhật khi bạn đang xem. Đang lấy giá mới nhất...");

            // Có thay đổi! Lấy ReadLock thật để đảm bảo giá đọc ra là giá cuối cùng
            stamp = sl.readLock();
            try {
                currentPrice = goldPrice;
            } finally {
                sl.unlockRead(stamp);
            }
        }

        System.out.println("[VIEW] " + userName + " thấy giá: " + currentPrice);
    }

    // 2. Dành cho Admin cập nhật giá vàng (Write Lock)
    private void updatePrice(double newPrice) {
        long stamp = sl.writeLock(); // Lấy khóa độc quyền
        try {
            System.out.println("\n>>> ADMIN: ĐANG CẬP NHẬT GIÁ VÀNG LÊN: " + newPrice + " <<<");
            try { TimeUnit.MILLISECONDS.sleep(100); } catch (InterruptedException e) {}
            this.goldPrice = newPrice;
        } finally {
            sl.unlockWrite(stamp);
            System.out.println(">>> ADMIN: CẬP NHẬT THÀNH CÔNG <<<\n");
        }
    }
}
