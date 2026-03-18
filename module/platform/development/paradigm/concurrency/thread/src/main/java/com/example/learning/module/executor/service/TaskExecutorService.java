package com.example.learning.module.executor.service;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class TaskExecutorService {
    @Async("springTaskExecutor")
    public void asyncTaskExecutor() throws InterruptedException {
        // Logic gửi mail nặng nề ở đây
        System.out.println("Doing some task at " + Thread.currentThread().getName());
        Thread.sleep(5000);
        System.out.println("Task finished at " + Thread.currentThread().getName());
    }
}
