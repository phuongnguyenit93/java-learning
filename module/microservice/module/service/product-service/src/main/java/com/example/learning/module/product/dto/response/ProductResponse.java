package com.example.learning.module.product.dto.response;

import java.math.BigDecimal;

public record ProductResponse(
        String name,
        String description,
        BigDecimal price
)
{ }
