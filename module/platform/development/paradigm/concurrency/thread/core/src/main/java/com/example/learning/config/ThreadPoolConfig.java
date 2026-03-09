package com.example.learning.config;

import com.example.learning.shared.ThreadLocalTaskDecorator;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;


@Getter @Setter
@RequiredArgsConstructor
@Configuration
public class ThreadPoolConfig {

    private final ThreadPoolProperties threadPoolProperties;
    private final ThreadLocalTaskDecorator threadLocalTaskDecorator;
    private final ThreadPoolRejectHandler threadPoolRejectHandler;

    @Bean("applicationTaskExecutor")
    public Executor taskExecutor() {
        final ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setTaskDecorator(threadLocalTaskDecorator);
        executor.setCorePoolSize(threadPoolProperties.getCoreSize());
        executor.setMaxPoolSize(threadPoolProperties.getMaxSize());
        executor.setQueueCapacity(threadPoolProperties.getQueueCapacity());
        executor.setKeepAliveSeconds(threadPoolProperties.getKeepAlive());
        executor.setThreadNamePrefix(threadPoolProperties.getThreadNamePrefix());
        executor.setRejectedExecutionHandler(threadPoolRejectHandler);

        // Quan trọng: Đảm bảo các luồng được đóng gọn gàng khi tắt App
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);

        executor.initialize();
        return executor;
    }

    @Bean(name = "defaultTaskExecutor")
    public Executor defaultTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setTaskDecorator(threadLocalTaskDecorator);
        int cores = Math.max(2, Runtime.getRuntime().availableProcessors());
        executor.setCorePoolSize(cores);
        executor.setMaxPoolSize(cores * 2);
        executor.setQueueCapacity(500);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("default-");
        executor.setRejectedExecutionHandler(threadPoolRejectHandler);


        // Quan trọng: Đảm bảo các luồng được đóng gọn gàng khi tắt App
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);

        executor.initialize();
        return executor;
    }

    @Bean(name = "interruptTaskExecutor")
    public Executor interruptTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setTaskDecorator(threadLocalTaskDecorator);
        int cores = Math.max(2, Runtime.getRuntime().availableProcessors());
        executor.setCorePoolSize(cores);
        executor.setMaxPoolSize(cores * 2);
        executor.setQueueCapacity(500);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("interrupt-");
        executor.setRejectedExecutionHandler(threadPoolRejectHandler);


        // Quan trọng: Đảm bảo các luồng được đóng gọn gàng khi tắt App
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(3);

        executor.initialize();
        return executor;
    }

    @Bean(name = "syncTaskExecutor")
    public Executor syncTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setTaskDecorator(threadLocalTaskDecorator);
        executor.setCorePoolSize(50);
        executor.setMaxPoolSize(50 * 2);
        executor.setQueueCapacity(1100000);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("interrupt-");
        executor.setRejectedExecutionHandler(threadPoolRejectHandler);


        // Quan trọng: Đảm bảo các luồng được đóng gọn gàng khi tắt App
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(3);

        executor.initialize();
        return executor;
    }
}
