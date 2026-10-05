package com.example.learning.module.context.config;

import com.example.learning.module.context.BlockingExperiment;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.reactive.config.BlockingExecutionConfigurer;
import org.springframework.web.reactive.config.WebFluxConfigurer;

@Configuration
public class ReactiveExperimentConfig implements WebFluxConfigurer {

    private final ThreadPoolTaskExecutor blockingExecutor = createBlockingExecutor();

    @Bean
    public AsyncTaskExecutor webFluxBlockingExecutor() {
        return blockingExecutor;
    }

    @Override
    public void configureBlockingExecution(BlockingExecutionConfigurer configurer) {
        configurer.setExecutor(blockingExecutor);
        configurer.setControllerMethodPredicate(handlerMethod ->
                handlerMethod.hasMethodAnnotation(BlockingExperiment.class)
        );
    }

    private static ThreadPoolTaskExecutor createBlockingExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("webflux-blocking-");
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(2);
        return executor;
    }
}
