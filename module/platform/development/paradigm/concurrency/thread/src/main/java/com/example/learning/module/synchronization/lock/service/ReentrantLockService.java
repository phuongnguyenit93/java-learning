package com.example.learning.module.synchronization.lock.service;

import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

@Service
public class ReentrantLockService {
    // Tạo khóa với chế độ công bằng (Fairness = true) để ai đến trước rút trước
    private final ReentrantLock fairLock = new ReentrantLock(true);

//    // Tạo khóa với chế độ không công bằng (Fairness = true) để ai giành đc trước thì vào
    private final ReentrantLock unfairLock = new ReentrantLock(false);

    public void reentrantLockSample () {
        // Khách hàng A rút tiền (mất 3 giây xử lý)
        Thread customerA = new Thread(() -> withdrawMoney("Khách hàng A"), "Thread-A");

        // Khách hàng B đến sau (chỉ chờ tối đa 2 giây)
        Thread customerB = new Thread(() -> withdrawMoney("Khách hàng B"), "Thread-B");

        customerA.start();
        customerB.start();
    }

    public void reentrantFairnessSample() throws InterruptedException {
        lockStarvationTest(fairLock);
    }

    public void reentrantUnfairnessSample() throws InterruptedException {
        lockStarvationTest(unfairLock);
    }

    public void interruptibleLockExample() throws InterruptedException {
        // Luồng 1 chiếm khóa và giữ rất lâu
        Thread t1 = new Thread(this::doLongTask, "Luồng-1");
        t1.start();

        Thread.sleep(1000); // Đảm bảo Luồng 1 đã lấy khóa

        // Luồng 2 vào đợi
        Thread t2 = new Thread(this::doLongTask, "Luồng-2");
        t2.start();

        Thread.sleep(2000); // Cho Luồng 2 đợi một chút

        // Sếp (Luồng chính) thấy lâu quá, ra lệnh dừng Luồng 2 lại
        System.out.println("Main: Luồng 2 đợi lâu quá rồi, bắt nó dừng lại thôi!");
        t2.interrupt();
    }

    public void doLongTask() {
        try {
            System.out.println(Thread.currentThread().getName() + " đang cố gắng lấy khóa...");

            // Nếu không lấy được khóa ngay, luồng sẽ đợi,
            // nhưng nếu bị interrupt() trong lúc đợi, nó sẽ ném ngoại lệ.
            fairLock.lockInterruptibly();

            try {
                System.out.println(Thread.currentThread().getName() + " đã lấy được khóa! Đang làm việc nặng...");
                Thread.sleep(10000); // Giả lập việc rất nặng (10 giây)
            } finally {
                fairLock.unlock();
            }
        } catch (InterruptedException e) {
            System.err.println(Thread.currentThread().getName() + " BỊ NGẮT QUÃNG khi đang đợi khóa! Huỷ bỏ tác vụ.");
        }
    }

    public void lockStarvationTest(ReentrantLock lock) throws InterruptedException {
        int THREAD_COUNT = 1000;
        int TOTAL_ITERATIONS = 1_000_000;
        System.out.println("--- ĐANG TEST STARVATION VỚI FAIR = " + lock.isFair() + " ---");
        AtomicInteger total = new AtomicInteger(0);
        Map<String, Integer> stats = new ConcurrentHashMap<>();

        Thread[] threads = new Thread[THREAD_COUNT];

        for (int i = 0; i < THREAD_COUNT; i++) {
            final String name = "Thread-" + i;
            threads[i] = new Thread(() -> {
                int count = 0;
                // Mỗi thread cố gắng lấy lock càng nhiều càng tốt cho đến khi đạt target chung
                while (total.get() < TOTAL_ITERATIONS) {
                    lock.lock();
                    try {
                        if (total.get()  >= TOTAL_ITERATIONS) break;
                        total.incrementAndGet();
                        count++;
                    } finally {
                        lock.unlock();
                    }
                }
                stats.put(name, count);
            }, name);
        }

        long start = System.currentTimeMillis();
        for (Thread t : threads) t.start();
        for (Thread t : threads) t.join();
        long end = System.currentTimeMillis();

        // Phân tích kết quả
        int max = stats.values().stream().max(Integer::compare).orElse(0);
        int min = stats.values().stream().min(Integer::compare).orElse(0);
        long zeroCount = stats.values().stream().filter(v -> v == 0).count();

        System.out.println("Thời gian chạy: " + (end - start) + " ms");
        System.out.println("Thread làm việc nhiều nhất: " + max + " lần");
        System.out.println("Thread làm việc ít nhất: " + min + " lần");
        System.out.println("Số Thread bị 'bỏ đói' hoàn toàn (0 lần): " + zeroCount);
    }

    private void withdrawMoney(String customerName) {
        System.out.println(customerName + " đang bước vào cây ATM...");

        try {
            // Thử lấy khóa trong vòng 2 giây. Nếu sau 2 giây không lấy được thì bỏ qua.
            if (fairLock.tryLock(2, TimeUnit.SECONDS)) {
                try {
                    System.out.println(customerName + " ĐÃ CHIẾM ĐƯỢC MÁY ATM.");
                    Thread.sleep(3000);
                    System.out.println(customerName + " rút tiền thành công!");
                } finally {
                    // CỰC KỲ QUAN TRỌNG: Phải luôn unlock trong khối finally
                    fairLock.unlock();
                    System.out.println(customerName + " đã rời khỏi cây ATM.");
                }
            } else {
                // Đây là tình huống thoát hiểm (Không bị Block vĩnh viễn)
                System.err.println(customerName + " KHÔNG ĐỢI ĐƯỢC NỮA: Cây ATM quá bận, tôi đi chỗ khác!");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
