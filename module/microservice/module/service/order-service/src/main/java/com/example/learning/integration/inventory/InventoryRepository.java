package com.example.learning.integration.inventory;

import com.example.learning.module.feign.exception.FeignClientException;
import com.example.learning.module.order.dto.OrderRequest;
import feign.FeignException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.net.ConnectException;

@Repository
@RequiredArgsConstructor
@Slf4j
public class InventoryRepository extends InventoryAdapter {
    private final InventoryClient inventoryClient;

    @Override
    @CircuitBreaker(name = "customCircuitConfig", fallbackMethod = "handleError")
    @Retryable(
            retryFor = { IOException.class, ConnectException.class, FeignException.class},
            noRetryFor = {FeignClientException.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 2000, multiplier = 1.5, maxDelay = 10000)
    )
    public boolean isInStock (String skuCode, Integer quantity) {
        return inventoryClient.isInStock(skuCode,quantity);
    }

    // Hàm Fallback cuối cùng cho cả 2
    public boolean handleError(String skuCode, Integer quantity, Throwable t) throws Throwable {
        log.error("Cả Retry và Circuit Breaker đều thất bại. Lỗi: {}", t.getMessage());
        System.out.println(t.getClass());
        throw t;
    }

    // 1. Xử lý khi Retry thất bại do lỗi hệ thống (Network, Timeout...)
    @Recover
    public boolean recoverSystemError(Exception e, String skuCode, Integer quantity) {
        log.error("==> RECOVER: Đã thử lại nhưng vẫn lỗi hệ thống ({}). Sku: {}",
                e.getMessage(), skuCode);
        // Có thể ném ra một Custom Exception để GlobalExceptionHandler bắt
        throw new FeignClientException(400, "Dịch vụ Inventory tạm thời không phản hồi. Vui lòng thử lại sau.");
    }

    // 2. Xử lý khi gặp lỗi FeignException (Ví dụ: 404, 500 từ phía đối tác)
    @Recover
    public boolean recoverFeignError(FeignException e, String skuCode, Integer quantity) {
        log.error("==> RECOVER: Lỗi từ phía Inventory Service: {}. Sku: {}",
                e.contentUTF8(), skuCode);
        throw e;
    }
}
