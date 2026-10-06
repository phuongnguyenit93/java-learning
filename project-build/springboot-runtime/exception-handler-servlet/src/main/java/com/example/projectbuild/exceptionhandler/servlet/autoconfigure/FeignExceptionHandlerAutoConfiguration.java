package com.example.projectbuild.exceptionhandler.servlet.autoconfigure;

import com.example.projectbuild.exceptionhandler.servlet.feign.FeignClientExceptionHandler;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;

@AutoConfiguration(after = ServletExceptionHandlerAutoConfiguration.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(name = {"feign.Feign", "feign.FeignException"})
public class FeignExceptionHandlerAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public FeignClientExceptionHandler feignClientExceptionHandler() {
        return new FeignClientExceptionHandler();
    }
}
