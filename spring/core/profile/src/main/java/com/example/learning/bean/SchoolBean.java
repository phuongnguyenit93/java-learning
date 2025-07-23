package com.example.learning.bean;

import com.example.learning.model.School;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
public class SchoolBean {
    @Value("${class.name}")
    String className;

    @Bean
    @Profile("dev")
    public School schoolDev() {
        return new School("School Dev",className);
    }

    @Bean
    @Profile("loc")
    public School schoolLoc() {
        return new School("School Local",className);
    }

    @Bean
    @Profile("prod")
    public School schoolProd() {
        return new School("School Product",className);
    }

}
