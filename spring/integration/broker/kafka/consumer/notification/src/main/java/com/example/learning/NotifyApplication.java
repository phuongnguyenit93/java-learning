package com.example.learning;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.springframework.kafka.annotation.EnableKafka;

@SpringBootApplication
public class NotifyApplication extends SpringBootServletInitializer {
	public static void main(String[] args) {
		SpringApplication.run(NotifyApplication.class, args);
	}
}
