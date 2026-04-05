package com.example.learning.module.order.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record OrderRequest (

        @NotBlank(message = "SKU Code không được để trống")
        String skuCode,

        @NotNull(message = "Giá không được để trống")
        @DecimalMin(value = "0.0", inclusive = false, message = "Giá phải lớn hơn 0")
        BigDecimal price,

        @NotNull(message = "Số lượng không được để trống")
        @Min(value = 1, message = "Số lượng phải ít nhất là 1")
        Integer quantity
) { }
