package com.example.learning.module.order.service;

import com.example.learning.integration.client.InventoryClient;
import com.example.learning.module.feign.exception.FeignClientException;
import com.example.learning.module.order.dto.OrderRequest;
import com.example.learning.module.order.mapper.OrderMapper;
import com.example.learning.module.order.model.Order;
import com.example.learning.module.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OrderService {
    private final OrderMapper orderMapper;
    private final OrderRepository orderRepository;
    private final InventoryClient inventoryClient;

    public void placeOrder (OrderRequest orderRequest) {
        boolean isProductInStock = inventoryClient.isInStock(orderRequest.skuCode(),orderRequest.quantity());

        if (isProductInStock) {
            Order order = orderMapper.toEntity(orderRequest);

            orderRepository.save(order);
        } else {
            throw new FeignClientException(422,"Product with SkuCode " + orderRequest.skuCode() + " is not in stock");
        }

    }
}
