package com.example.learning.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Ánh xạ URL /assets/abc.png vào thư mục src/main/resources/my-assets/
        registry.addResourceHandler("/resources/**")
                .addResourceLocations("classpath:/static/");
    }
}
