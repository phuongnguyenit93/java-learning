package com.example.learning.module.pool.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "thread.thread-pool")
@Data
public class ThreadPoolProperties {
    private int coreSize;
    private int maxSize;
    private int queueCapacity;
    private int keepAlive;
    private String threadNamePrefix;
}
