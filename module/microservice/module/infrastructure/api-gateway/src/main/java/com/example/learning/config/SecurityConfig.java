package com.example.learning.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

@EnableWebSecurity
@Configuration
public class SecurityConfig {
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/login/**", "/assets/**","/actuator/**").permitAll()
                        // Tất cả các route đi qua Gateway đều cần đăng nhập
                        .anyRequest().authenticated()
                )
                // 1. Cho phép đăng nhập qua giao diện Keycloak (nếu vào bằng trình duyệt)
                .oauth2Login(Customizer.withDefaults())

                // 2. Cho phép xác thực bằng Token (nếu gọi từ Postman/Mobile)
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));

        return http.build();
    }
}
