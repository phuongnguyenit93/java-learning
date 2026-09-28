package com.example.learning.module.forkjoin.service;

import com.example.learning.module.forkjoin.task.ForkJoinProcessor;
import com.example.learning.shared.entity.Transaction;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ForkJoinPool;

@Service
public class ForkJoinService {
    public void forkJoinExample() {
        // Tạo 1 triệu bản ghi với dữ liệu random
        System.out.println("Đang tạo 100 triệu records...");
        List<Transaction> data = new ArrayList<>(100_000_000);
        long startInitData = System.currentTimeMillis();
        for (int i = 0; i < 100_000_000; i++) {
            data.add(new Transaction(i));
        }
        long endInitData = System.currentTimeMillis();

        ForkJoinPool pool = new ForkJoinPool();

        System.out.println("Bắt đầu xử lý song song với Fork/Join...");
        long startTimeFork = System.currentTimeMillis();

        List<Long> invalidRecords = pool.invoke(new ForkJoinProcessor(data, 0, data.size()));

        long endTimeFork = System.currentTimeMillis();

        System.out.println("Bắt đầu xử lý song song với Parallel Stream...");
        long startTimeParallel = System.currentTimeMillis();

        List<Long> invalidRecordsWithParallel = data.parallelStream() // Tự động dùng Parallel Stream
                .filter(t -> !t.isValid())
                .map(t -> t.getId())
                .toList();

        long endTimeParallel = System.currentTimeMillis();

        System.out.println("--- Kết quả ---");
        System.out.println("Tìm thấy " + invalidRecords.size() + " bản ghi lỗi (âm).");
        System.out.println("Thời gian xử lý tạo data: " + (endInitData - startInitData) + "ms");
        System.out.println("Thời gian xử lý fork: " + (endTimeFork - startTimeFork) + "ms");
        System.out.println("Thời gian xử lý parallel: " + (endTimeParallel - startTimeParallel) + "ms");
    }
}
