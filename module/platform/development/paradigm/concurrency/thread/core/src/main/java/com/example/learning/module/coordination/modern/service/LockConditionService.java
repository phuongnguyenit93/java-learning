package com.example.learning.module.coordination.modern.service;

import org.springframework.stereotype.Service;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

@Service
public class LockConditionService {
    private final Lock lock = new ReentrantLock();

    // Tạo 2 điều kiện riêng biệt từ 1 cái Lock
    private final Condition coffeeAvailable = lock.newCondition();
    private final Condition waterNeeded = lock.newCondition();

    private int waterLevel = 0;
    private final int MAX_WATER = 3;

    public void conditionLockExample() {
        // 3 Nhân viên đến máy cùng lúc
        for (int i = 1; i <= 3; i++) {
            String name = "Nhân viên " + i;
            new Thread(() -> getCoffee(name)).start();
        }

        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        // Kỹ thuật viên đi nạp nước
        new Thread(this::refillWater).start();
    }

    private void getCoffee(String staffName) {
        lock.lock();
        try {
            while (waterLevel == 0) {
                System.out.println(staffName + " thấy máy hết nước, đứng đợi...");
                coffeeAvailable.await(); // Đợi đúng ở danh sách "đợi cà phê"
            }
            waterLevel--;
            System.out.println(staffName + " đã lấy 1 ly. Nước còn: " + waterLevel);

            // Nếu sắp hết nước, báo cho kỹ thuật viên
            if (waterLevel == 0) {
                waterNeeded.signal(); // Đánh thức đúng kỹ thuật viên
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            lock.unlock();
        }
    }

    // Phương thức cho Kỹ thuật viên nạp nước
    private void refillWater() {
        lock.lock();
        try {
            while (waterLevel > 0) {
                System.out.println("KT viên: Nước vẫn còn, chưa cần nạp thêm...");
                waterNeeded.await(); // Đợi ở danh sách "đợi nạp nước"
            }
            System.out.println("KT viên: Đang nạp đầy nước...");
            waterLevel = MAX_WATER;

            // Nạp xong thì báo cho NHÂN VIÊN
            coffeeAvailable.signalAll(); // Đánh thức đúng nhóm đang đợi cà phê
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            lock.unlock();
        }
    }
}
