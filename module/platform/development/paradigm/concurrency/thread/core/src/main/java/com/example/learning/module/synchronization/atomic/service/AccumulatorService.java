package com.example.learning.module.synchronization.atomic.service;

import org.springframework.stereotype.Service;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAccumulator;
import java.util.function.LongBinaryOperator;

@Service
public class AccumulatorService {
    public void accumulatorExample() throws InterruptedException {
        // 1. Định nghĩa công thức phức tạp qua Lambda
        // current là giá trị tích lũy hiện tại, addedValue là giá trị mới đưa vào
        LongBinaryOperator complexLogic = (current, addedValue) -> {
            long tax = addedValue / 10; // Giả sử phí là 10%
            return current + addedValue + tax;
        };

        // 2. Khởi tạo Accumulator với công thức trên và giá trị gốc là 0
        LongAccumulator totalAccount = new LongAccumulator(complexLogic, 0L);

        ExecutorService executor = Executors.newFixedThreadPool(5);

        // 3. Giả lập 5 luồng nạp tiền cùng lúc
        long[] deposits = {1000, 2000, 3000, 4000, 5000};

        for (long amount : deposits) {
            executor.execute(() -> {
                System.out.println(Thread.currentThread().getName() + " nạp: " + amount);
                totalAccount.accumulate(amount);
            });
        }

        executor.shutdown();
        executor.awaitTermination(30, TimeUnit.SECONDS);

        // 4. Kết quả: Tổng = (1000+100) + (2000+200) + (3000+300) + (4000+400) + (5000+500)
        // Tổng kỳ vọng: 15000 + 1500 = 16500
        System.out.println("------------------------------------");
        System.out.println("Tổng số dư cuối cùng (bao gồm phí): " + totalAccount.get());
    }
}
