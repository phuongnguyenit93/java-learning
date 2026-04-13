package com.example.learning.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

@Configuration
@EnableWebSecurity
public class SecurityConfig {
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        SavedRequestAwareAuthenticationSuccessHandler successHandler =
                new SavedRequestAwareAuthenticationSuccessHandler();
        successHandler.setTargetUrlParameter("redirectTo");
        successHandler.setDefaultTargetUrl("/");

        http
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/assets/**", "/login", "/actuator/health/**", "/actuator/info/**").permitAll()

                        // 1. Chỉ ADMIN mới được phép thực hiện các hành động thay đổi (POST, DELETE, PUT)
                        // Các endpoint của SBA như /instances/** lo việc điều khiển service
                        // Chặn tất cả các thao tác tác động đến Instance (bao gồm shutdown, change log level, v.v.)
                        .requestMatchers(HttpMethod.POST, "/instances/**", "/applications/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/instances/**", "/applications/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/instances/**", "/applications/**").hasRole("ADMIN")
                        .requestMatchers("/actuator/shutdown").hasRole("ADMIN")

                        // 2. USER và ADMIN đều có thể xem Dashboard (GET)
                        .anyRequest().permitAll()
                )
                // 3. Cấu hình Form Login
                .formLogin(form -> form.loginPage("/login").successHandler(successHandler))
                // 4. Cấu hình Logout
                .logout(logout -> logout.logoutUrl("/logout"))
                // 5. Mở login bằng HTTP Basic để các Client có thể gửi data lên
                .httpBasic(Customizer.withDefaults())
                // 6. Tắt CSRF để các Client (Eureka/Service con) có thể POST dữ liệu vào
                .rememberMe(remember -> remember.key("uniqueAndSecret")) // Giúp bạn không phải login lại nhiều lần
//                .sessionManagement(session -> session
//                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED) // Chỉ tạo session khi cần
//                        .maximumSessions(1) // Giới hạn 1 người dùng chỉ log được 1 chỗ (tăng bảo mật)
//                )
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .ignoringRequestMatchers("/instances","/instances/**","/actuator/**","/logout","/applications/**")
                );


        return http.build();
    }
}
