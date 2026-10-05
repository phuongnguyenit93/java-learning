package com.example.learning.module.web.input.model;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record CreateOrderRequest(
        @NotBlank(message = "item must not be blank")
        String item,
        @Min(value = 1, message = "quantity must be at least 1")
        int quantity
) {
}
