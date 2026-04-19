package com.example.learning.module.order.controller;

import com.example.learning.module.order.dto.OrderRequest;
import com.example.learning.module.order.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("api/order")
@RequiredArgsConstructor
public class OrderController {
    private final OrderService orderService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public String placeOrder(@Valid @RequestBody OrderRequest orderRequest) {
        orderService.placeOrder(orderRequest);

        return "Place Order Successfully";
    }

    private static final Logger log = LoggerFactory.getLogger(OrderController.class);

    @GetMapping("/test-tracing")
    public String test() {
        log.info("Checking Trace ID in logs...");
        return "Check your console!";
    }
}
