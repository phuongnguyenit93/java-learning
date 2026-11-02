package com.example.learning.casestudy.domain.group7.case5;

public record OrderId(String value) {
    public OrderId {
        if (value == null || value.isBlank())throw new IllegalArgumentException();

    }
}
