package com.example.projectbuild.executioncontext.servlet;

import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

public class ExecutionContextWebMvcConfiguration implements WebMvcConfigurer {

    private final ExecutionContextHandlerInterceptor interceptor;

    public ExecutionContextWebMvcConfiguration(ExecutionContextHandlerInterceptor interceptor) {
        this.interceptor = interceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(interceptor);
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addRedirectViewController(
                "/execution-context",
                "/execution-context/explorer.html"
        );
    }
}
