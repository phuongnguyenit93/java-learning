package com.example.learning.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.properties.SwaggerUiConfigParameters;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
@RequiredArgsConstructor
public class SwaggerAutoConfig extends OncePerRequestFilter {
    private final DiscoveryClient discoveryClient;
    private final SwaggerUiConfigParameters swaggerUiParameters;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        if (request.getRequestURI().contains("/v3/api-docs/swagger-config")) {

            // 1. Lấy danh sách tên các service đang ACTIVE từ Eureka
            List<String> activeServices = discoveryClient.getServices().stream()
                    .filter(s -> !s.toLowerCase().contains("gateway") && !s.toLowerCase().contains("admin"))
                    .map(String::toLowerCase)
                    .toList();

            // 2. REMOVE: Dọn dẹp các Group cũ không còn nằm trong danh sách Active
            // swaggerUiParameters.getUrls() trả về danh sách các Group hiện tại
            if (swaggerUiParameters.getUrls() != null) {
                swaggerUiParameters.getUrls().removeIf(url -> {
                    String groupName = url.getName().toLowerCase();
                    // Nếu groupName không nằm trong danh sách service đang sống -> Xóa
                    boolean shouldRemove = !activeServices.contains(groupName);
                    if (shouldRemove) {
                        System.out.println(">>> Removing stale service from Swagger: " + groupName);
                    }
                    return shouldRemove;
                });
            }

            // 3. ADD: Thêm các service mới chưa có trong Group
            activeServices.forEach(name -> {
                // addGroup của SpringDoc khá thông minh, nó sẽ không add trùng nếu đã có
                swaggerUiParameters.addGroup(name, "/v3/api-docs/" + name);
            });

            System.out.println(">>> Swagger groups synced with Eureka. Active count: " + activeServices.size());
        }

        // Cho phép request đi tiếp đến Controller mặc định của SpringDoc
        filterChain.doFilter(request, response);
    }
}
