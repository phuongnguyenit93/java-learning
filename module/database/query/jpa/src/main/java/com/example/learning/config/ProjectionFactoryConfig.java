package com.example.learning.config;

import org.springframework.context.annotation.Bean;
import org.springframework.data.projection.ProjectionFactory;
import org.springframework.data.projection.SpelAwareProxyProjectionFactory;

public class ProjectionFactoryConfig {
    @Bean
    public ProjectionFactory getProjectionFactory() {
        ProjectionFactory pf = new SpelAwareProxyProjectionFactory();
        return pf;
    }
}
