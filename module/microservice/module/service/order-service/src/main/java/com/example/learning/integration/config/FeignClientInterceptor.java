package com.example.learning.integration.config;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

public class FeignClientInterceptor implements RequestInterceptor {
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_TOKEN_TYPE = "Bearer";

    @Override
    public void apply(RequestTemplate template) {
        // 1. Lấy thông tin xác thực từ SecurityContext của Spring Security
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        // 2. Kiểm tra xem có phải là JWT Token không
        if (authentication instanceof JwtAuthenticationToken jwtAuthenticationToken) {
            String tokenValue = jwtAuthenticationToken.getToken().getTokenValue();

            // 3. Đính kèm Token vào Header của request Feign sắp gửi đi
            template.header(AUTHORIZATION_HEADER, String.format("%s %s", BEARER_TOKEN_TYPE, tokenValue));
        }
    }
}
