package com.example.learning.casestudy.domain.group1.case4;

public record CustomerId(String value) {
    public CustomerId {
        if (value == null || value.isBlank())
            throw new IllegalArgumentException("customerId");
    }
}
