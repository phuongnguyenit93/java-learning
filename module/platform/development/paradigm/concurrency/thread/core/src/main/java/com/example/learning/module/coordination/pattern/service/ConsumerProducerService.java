package com.example.learning.module.coordination.pattern.service;

import org.springframework.stereotype.Service;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

@Service
public class ConsumerProducerService {
    public void consumerProducerPatternExample() {
        // Buffer: Chỉ chứa tối đa 5 khung hình để tiết kiệm RAM
        BlockingQueue<String> buffer = new ArrayBlockingQueue<>(5);

        // PRODUCER: Luồng đọc dữ liệu
        Thread reader = new Thread(() -> {
            try {
                for (int i = 1; i <= 10; i++) {
                    String frame = "Frame-" + i;
                    System.out.println("Producer: Đang đọc " + frame);

                    // put() sẽ tự động đợi (wait) nếu buffer đầy
                    buffer.put(frame);

                    System.out.println("Producer: Đã đẩy " + frame + " vào kho.");
                    Thread.sleep(100); // Tốc độ đọc file thường rất nhanh
                }
            } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        });

        // CONSUMER: Luồng Encode video
        Thread encoder = new Thread(() -> {
            try {
                while (true) {
                    // take() sẽ tự động đợi (wait) nếu buffer trống
                    String frame = buffer.take();

                    System.out.println("Consumer: ===> Đang render " + frame);
                    Thread.sleep(1000); // Việc render rất nặng và chậm
                }
            } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        });

        reader.start();
        encoder.start();
    }
}
