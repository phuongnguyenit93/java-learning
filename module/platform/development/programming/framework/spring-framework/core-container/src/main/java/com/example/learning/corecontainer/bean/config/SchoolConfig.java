package com.example.learning.corecontainer.bean.config;

import com.example.learning.corecontainer.bean.model.School;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class SchoolConfig {
    @Bean
    @Primary
    @Qualifier("schoolA")
    public School schoolA() {
        return new School("School A");
    }

    @Bean
    @Qualifier("schoolB")
    public School schoolB() {
        return new School("School B");
    }
}

