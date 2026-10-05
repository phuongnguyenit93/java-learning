package com.example.learning;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@EnableTransactionManagement
@SpringBootApplication
public class SpringTransactionManagementApplication extends SpringBootServletInitializer {

    public static void main(String[] args) {
        SpringApplication.run(SpringTransactionManagementApplication.class, args);
    }
}
