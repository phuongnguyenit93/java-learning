package com.example.learning.component;

import lombok.Getter;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ProxyMetadataCollector implements BeanPostProcessor {
    // Map này sẽ lưu: BeanName -> RealClassName ($Proxy...)
    @Getter
    private static final Map<String, String> proxyMap = new ConcurrentHashMap<>();

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        // Chỉ quét các bean trong package của bạn để tránh làm nặng hệ thống
        proxyMap.put(beanName, bean.getClass().getName());

        return bean;
    }
}
