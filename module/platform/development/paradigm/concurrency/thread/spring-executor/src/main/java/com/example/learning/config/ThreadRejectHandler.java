package com.example.learning.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;

@Slf4j
@Component
public class ThreadRejectHandler implements RejectedExecutionHandler {
    @Override
    public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
        // 1. Ghi log cảnh báo
        log.error("Hệ thống quá tải! Task {} bị từ chối. Pool size: {}, Queue: {}",
                r.toString(), executor.getPoolSize(), executor.getQueue().size());

        // 3. Có thể ném lỗi hoặc thực hiện chiến lược dự phòng
        throw new RejectedExecutionException("Task rejected due to resource limits");
    }
}
