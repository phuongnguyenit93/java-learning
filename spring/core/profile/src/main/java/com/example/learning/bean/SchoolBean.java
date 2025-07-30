package com.example.learning.bean;

import com.example.learning.model.School;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
public class SchoolBean {
    @Value("${school.name}")
    String schoolName;

    @Value("${class.name}")
    String className;

    @Value("${student.name}")
    String studentName;

    public School createSchool() {
        return new School(schoolName,className,studentName);
    }

    @Bean
    @Profile("dev")
    public School schoolDev() {
        return createSchool();
    }

    @Bean
    @Profile("loc")
    public School schoolLoc() {
        return createSchool();
    }

    @Bean
    @Profile("prod")
    public School schoolProd() {
        return createSchool();
    }

}
