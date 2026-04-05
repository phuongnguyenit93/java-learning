package com.example.learning.listener;

import lombok.RequiredArgsConstructor;
import org.springdoc.core.properties.SwaggerUiConfigParameters;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.client.discovery.event.HeartbeatEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class EurekaListener {
    private final DiscoveryClient discoveryClient;
    private final SwaggerUiConfigParameters swaggerUiParameters;

    // Sự kiện này bắn ra mỗi khi Eureka Client cập nhật danh sách từ Server (mặc định 30s/lần)
    // Hoặc khi có sự thay đổi về số lượng instance
    @EventListener(HeartbeatEvent.class)
    public void onEurekaRefresh() {
        // 1. Lấy danh sách service mới nhất
        List<String> activeServices = discoveryClient.getServices().stream()
                .filter(s -> !s.toLowerCase().contains("gateway") && !s.toLowerCase().contains("admin"))
                .map(String::toLowerCase)
                .toList();

        // 2. Dọn dẹp Group cũ (Remove)
        if (swaggerUiParameters.getUrls() != null) {
            swaggerUiParameters.getUrls().removeIf(url -> !activeServices.contains(url.getName().toLowerCase()));
        }

        // 3. Thêm Group mới (Add)
        activeServices.forEach(name ->
                swaggerUiParameters.addGroup(name, "/v3/api-docs/" + name)
        );
    }
}
