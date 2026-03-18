package com.example.learning.module.synchronization.basic.service;

import com.example.learning.module.synchronization.basic.entity.LivelockEntity;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import org.springframework.stereotype.Service;

@Service
public class SyncProblemService {
    // Chuyển ra ngoài làm biến static
    private static int x, y, a, b;
    private static boolean running = true;;
    private int count = 0;

    public void instructionReordering() throws InterruptedException {
        long count = 0;
        while (true) {
            count++;
            x = 0; y = 0; a = 0; b = 0;

            // Dùng CountDownLatch để hai Thread xuất phát cùng lúc
            java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);

            Thread t1 = new Thread(() -> {
                try { latch.await(); } catch (InterruptedException e) {}
                x = 1;      // Lệnh 1
                a = y;      // Lệnh 2
            });

            Thread t2 = new Thread(() -> {
                try { latch.await(); } catch (InterruptedException e) {}
                y = 1;      // Lệnh 3
                b = x;      // Lệnh 4
            });

            t1.start();
            t2.start();
            latch.countDown(); // Nổ súng!

            t1.join();
            t2.join();

            // Kiểm tra kết quả "vô lý"
            if (a == 0 && b == 0) {
                System.err.println("LẦN THỨ " + count + ": PHÁT HIỆN REORDERING! (a=0 , b=0)");
                break;
            }

            if (count % 100000 == 0) {
                System.out.println("Đã chạy " + count + " lần mà chưa bắt được Reordering...");
            }
        }
    }

    public void staleData () throws InterruptedException {
        running = true;
        Thread t1 = new Thread(() -> {
            long count = 0;
            while (running) {
                // TUYỆT ĐỐI ĐỂ TRỐNG: Không Println, không Sleep
                // Một dòng comment cũng được, nhưng không có lệnh nào thực thi
                count++;
            }
            System.out.println("Thread 1 dừng lại sau " + count + " vòng lặp.");
        });

        t1.start();
        Thread.sleep(100); // Cho Thread 1 chạy vào vòng lặp trước

        System.out.println("Thread chính chuẩn bị đổi running = false...");
        running = false; // Đổi biến
        System.out.println("Thread chính đã đổi xong. Đợi Thread 1 dừng...");

        t1.join(5000); // Đợi tối đa 5 giây
        if (t1.isAlive()) {
            System.err.println("THẮNG RỒI! Thread 1 bị kẹt (Stale Data thành công)!");
            t1.interrupt();
            System.out.println(t1.isInterrupted());
        }
    }

    public void raceCondition() throws InterruptedException  {
        count = 0;
        Thread[] threads = new Thread[100];

        for (int i = 0; i < 100; i++) {
            threads[i] = new Thread(() -> {
                for (int j = 0; j < 1000; j++) {
                    count++; // Race condition xảy ra ở đây
                }
            });
            threads[i].start();
        }

        for (Thread t : threads) t.join();

        System.out.println("Kết quả cuối cùng: " + count);
        // Chắc chắn sẽ nhỏ hơn 100.000
    }

    public void deadlock() {
        // Thay Object bằng ReentrantLock để có thể dùng tryLock
        ReentrantLock lock1 = new ReentrantLock();
        ReentrantLock lock2 = new ReentrantLock();

        Thread t1 = new Thread(() -> {
            try {
                // Thử lấy lock1 trong tối đa 5 giây
                if (lock1.tryLock(5, TimeUnit.SECONDS)) {
                    try {
                        System.out.println("Thread 1: Đang giữ lock 1...");
                        Thread.sleep(100); // Giả lập công việc

                        System.out.println("Thread 1: Đang thử lấy lock 2...");
                        // Thử lấy lock2 trong tối đa 5 giây
                        if (lock2.tryLock(5, TimeUnit.SECONDS)) {
                            try {
                                System.out.println("Thread 1: Đã lấy được cả 2 lock!");
                            } finally {
                                lock2.unlock();
                            }
                        } else {
                            System.err.println("Thread 1: Quá 5s không lấy được lock 2, từ bỏ!");
                        }
                    } finally {
                        lock1.unlock();
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        Thread t2 = new Thread(() -> {
            try {
                if (lock2.tryLock(5, TimeUnit.SECONDS)) {
                    try {
                        System.out.println("Thread 2: Đang giữ lock 2...");
                        Thread.sleep(100);

                        System.out.println("Thread 2: Đang thử lấy lock 1...");
                        if (lock1.tryLock(5, TimeUnit.SECONDS)) {
                            try {
                                System.out.println("Thread 2: Đã lấy được cả 2 lock!");
                            } finally {
                                lock1.unlock();
                            }
                        } else {
                            System.err.println("Thread 2: Quá 5s không lấy được lock 1, từ bỏ!");
                        }
                    } finally {
                        lock2.unlock();
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        t1.start();
        t2.start();
    }

    public void livelock() {
        LivelockEntity serverA = new LivelockEntity("Server-A");
        LivelockEntity serverB = new LivelockEntity("Server-B");

        serverA.setOtherServer(serverB);
        serverB.setOtherServer(serverA);

        // Chạy hai server cùng lúc
        new Thread(serverA).start();
        new Thread(serverB).start();
    }
}
