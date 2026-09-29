package com.example.learning.module.executor.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
@EnableAsync
public class TaskExecutorConfig implements AsyncConfigurer {

    private final AsyncExceptionProbe asyncExceptionProbe;

    public TaskExecutorConfig(AsyncExceptionProbe asyncExceptionProbe) {
        this.asyncExceptionProbe = asyncExceptionProbe;
    }

    @Bean("threadLearningTaskExecutor")
    public Executor threadLearningTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(8);
        executor.setKeepAliveSeconds(30);
        executor.setThreadNamePrefix("thread-learning-executor-");
        executor.setTaskDecorator(new DemoTaskDecorator());
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(2);
        return executor;
    }

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return asyncExceptionProbe;
    }
}
