package com.example.projectbuild.swagger.reactive;

import org.springframework.web.reactive.config.ResourceHandlerRegistry;
import org.springframework.web.reactive.config.WebFluxConfigurer;

public class ReactiveSwaggerResourceConfiguration implements WebFluxConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry
                .addResourceHandler("/readme/**")
                .addResourceLocations("classpath:/META-INF/swagger/readme/");
    }

}
