package com.example.learning.module.product.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record ProductRequest (
    @NotBlank(message = "Tên không được để trống") // Check không trống, không chỉ chứa dấu cách
    String name,

    @NotBlank(message = "Mô tả không được để trống") // Check không trống, không chỉ chứa dấu cách
    String description,

    @NotNull(message = "Giá không được để trống")
    @DecimalMin(value = "0.0", inclusive = false, message = "Giá phải lớn hơn 0")
    BigDecimal price
){}
