package com.example.learning.module.synchronization.service;

import com.example.learning.module.synchronization.entity.SyncEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;

@Service
@RequiredArgsConstructor
public class SyncService {

    @Qualifier("syncTaskExecutor")
    private final Executor syncTaskExecutor;

    private final SyncEntity syncEntity;

    public void executeSyncTask() throws InterruptedException {
        System.out.println("=== BẮT ĐẦU BENCHMARK (1 TRIỆU TASKS - 100 THREADS) ===");

        // 1. Đo Unsafe (Dữ liệu sai nhưng tốc độ nhanh nhất vì không có rào cản)
        long timeUnsafe = runSyncTest(syncEntity::incrementUnsafe);

        // 2. Đo Synchronized (Dữ liệu đúng nhưng chậm vì phải xếp hàng - Lock)
        long timeSync = runSyncTest(syncEntity::incrementSafeSync);

        // 3. Đo Atomic (Dữ liệu đúng và tốc độ tối ưu - Lock-free)
        long timeAtomic = runSyncTest(syncEntity::incrementAtomic);

        System.out.println("\n=== BẢNG SO SÁNH HIỆU NĂNG ===");
        System.out.printf("| %-15s | %-12s | %-12s |\n", "Phương pháp", "Thời gian", "Kết quả");
        System.out.printf("| %-15s | %-10d ms | %-12d |\n", "Unsafe", timeUnsafe, syncEntity.getUnsafeCount());
        System.out.printf("| %-15s | %-10d ms | %-12d |\n", "Synchronized", timeSync, syncEntity.getSafeCountWithSync());
        System.out.printf("| %-15s | %-10d ms | %-12d |\n", "Atomic", timeAtomic, syncEntity.getAtomicCount().get());

        syncEntity.reset();
    }


    public long runSyncTest(Runnable task) throws InterruptedException {
        int totalTask = 1_000_000;
        CountDownLatch startSignal = new CountDownLatch(1);
        CountDownLatch doneSignal = new CountDownLatch(totalTask);

        for (int i = 0; i < totalTask; i++) {
            syncTaskExecutor.execute(() -> {
                try {
                    startSignal.await();
                    task.run();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneSignal.countDown();
                }
            });
        }

        long startTime = System.nanoTime();
        startSignal.countDown();

        // Chỉ cần đợi doneSignal về 0 là biết 1 triệu task đã xong
        doneSignal.await();

        long endTime = System.nanoTime();

        // KHÔNG gọi shutdown() ở đây!
        return (endTime - startTime) / 1_000_000; // Đổi sang miliseconds
    }


}
