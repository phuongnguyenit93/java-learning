package com.example.learning.casestudy.dto.group2.case14;

// Dạng dữ liệu mà JSON binding có thể cung cấp từ input bên ngoài.
public record PaymentDto(String reference, long amountMinor) {
}
