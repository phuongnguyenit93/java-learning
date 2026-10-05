package com.example.learning;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;

@SpringBootApplication
public class SpringJmsApplication extends SpringBootServletInitializer {

    public static void main(String[] args) {
        SpringApplication.run(SpringJmsApplication.class, args);
    }
}
