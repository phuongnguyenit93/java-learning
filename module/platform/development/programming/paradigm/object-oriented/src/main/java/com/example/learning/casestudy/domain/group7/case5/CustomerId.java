package com.example.learning.casestudy.domain.group7.case5;

public record CustomerId(String value) {
    public CustomerId {
        if (value == null || value.isBlank())throw new IllegalArgumentException();

    }
}
