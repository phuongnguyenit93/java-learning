package com.example.learning;

import com.example.learning.config.CustomRepositoryConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@EnableJpaRepositories(repositoryBaseClass = CustomRepositoryConfig.class)
public class JPAApplication {
	public static void main(String[] args) {
		System.out.println("Current directory: " + System.getProperty("user.dir"));
		SpringApplication.run(JPAApplication.class, args);
	}
}
