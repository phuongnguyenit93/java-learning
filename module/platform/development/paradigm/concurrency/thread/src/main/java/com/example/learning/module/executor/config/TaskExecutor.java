package com.example.learning.module.executor.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync
@RequiredArgsConstructor
public class TaskExecutor {
    private final ThreadTaskDecorator threadTaskDecorator;
    private final ThreadRejectHandler threadRejectHandler;

    @Bean(name = "springTaskExecutor")
    public Executor springTaskExecutor() {
        final ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        executor.setTaskDecorator(threadTaskDecorator);
        int cores = Math.max(2, Runtime.getRuntime().availableProcessors());
        executor.setCorePoolSize(cores);
        executor.setMaxPoolSize(cores * 2);
        executor.setQueueCapacity(500);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("spring-executor-");
        executor.setRejectedExecutionHandler(threadRejectHandler);

        // Quan trọng: Đảm bảo các luồng được đóng gọn gàng khi tắt App
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);

        executor.initialize();
        return executor;
    }

}
