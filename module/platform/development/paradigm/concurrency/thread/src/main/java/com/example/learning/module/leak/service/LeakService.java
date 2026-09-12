package com.example.learning.module.leak.service;

import org.springframework.stereotype.Service;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class LeakService {

    private final AtomicInteger threadCounter = new AtomicInteger();
    private final AtomicInteger poolCounter = new AtomicInteger();

    /**
     * DEMO THREAD LEAK
     *
     * Mỗi lần gọi sẽ tạo trực tiếp 1 Thread mới.
     * Thread này chạy vô hạn và không có cơ chế stop.
     *
     * Gọi endpoint nhiều lần => số thread tăng liên tục.
     */
    public String createThreadLeak() {

        int id = threadCounter.incrementAndGet();

        Thread thread = new Thread(() -> {

            while (true) {
                try {
                    Thread.sleep(60_000);
                } catch (InterruptedException e) {
                    // Cố tình bỏ qua để demo leak
                }
            }

        }, "leaked-thread-" + id);

        thread.start();

        return "Created thread: " + thread.getName();
    }


    /**
     * DEMO POOL LEAK
     *
     * Mỗi lần gọi sẽ tạo một FixedThreadPool mới gồm 3 threads.
     * Không shutdown pool và không giữ reference.
     *
     * => Pool bị leak.
     * => Các worker thread của pool cũng bị leak.
     */
    public String createPoolLeak() {

        int poolId = poolCounter.incrementAndGet();

        AtomicInteger workerCounter = new AtomicInteger();

        ExecutorService pool =
                Executors.newFixedThreadPool(
                        3,
                        runnable -> {
                            Thread thread = new Thread(runnable);

                            thread.setName(
                                    "leaked-pool-"
                                            + poolId
                                            + "-thread-"
                                            + workerCounter.incrementAndGet()
                            );

                            return thread;
                        }
                );

        for (int i = 1; i <= 3; i++) {

            int taskId = i;

            pool.execute(() -> {

                System.out.println(
                        "Task "
                                + taskId
                                + " running on "
                                + Thread.currentThread().getName()
                );

                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }

        // CỐ TÌNH:
        // không pool.shutdown()
        // không lưu pool vào field / Map

        return "Created leaked pool: leaked-pool-" + poolId;
    }
}