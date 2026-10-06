package com.example.projectbuild.exceptionhandler.servlet.autoconfigure;

import com.example.projectbuild.exceptionhandler.servlet.mongo.MongoExceptionHandler;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;

@AutoConfiguration(after = ServletExceptionHandlerAutoConfiguration.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(name = {
        "org.springframework.data.mongodb.core.MongoTemplate",
        "org.springframework.dao.DuplicateKeyException"
})
public class MongoExceptionHandlerAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public MongoExceptionHandler mongoExceptionHandler() {
        return new MongoExceptionHandler();
    }
}
