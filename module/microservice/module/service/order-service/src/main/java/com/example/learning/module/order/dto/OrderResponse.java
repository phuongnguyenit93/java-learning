package com.example.learning.module.order.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record OrderResponse (
        String orderNumber,
        String skuCode,
        BigDecimal price,
        Integer quantity
) {}
