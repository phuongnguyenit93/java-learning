package com.example.learning.module.order.service;

import com.example.learning.integration.inventory.InventoryClient;
import com.example.learning.integration.inventory.InventoryRepository;
import com.example.learning.module.feign.exception.FeignClientException;
import com.example.learning.module.order.dto.OrderRequest;
import com.example.learning.module.order.mapper.OrderMapper;
import com.example.learning.module.order.model.Order;
import com.example.learning.module.order.repository.OrderRepository;
import feign.FeignException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.ConnectException;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {
    private final OrderMapper orderMapper;
    private final OrderRepository orderRepository;
    private final InventoryRepository inventoryRepository;

    public void placeOrder (OrderRequest orderRequest) {
        System.out.println("Running now");
        boolean isProductInStock = inventoryRepository.isInStock(orderRequest.skuCode(),orderRequest.quantity());

        if (isProductInStock) {
            Order order = orderMapper.toEntity(orderRequest);

            orderRepository.save(order);
        } else {
            throw new FeignClientException(422,"Product with SkuCode " + orderRequest.skuCode() + " is not in stock");
        }
    }
}
