package com.example.learning.oop.polymorphism.model;

public record PaymentOutcome(Status status, String detail) {
    public enum Status { PAID, DECLINED, PENDING }
    public PaymentOutcome {
        if (status == null || detail == null || detail.isBlank()) {
            throw new IllegalArgumentException("status and detail are required");
        }
    }
}
