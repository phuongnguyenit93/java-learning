package com.example.learning.casestudy.domain.group2.case2;

public record CustomerId(String value) {
    public CustomerId {
        if (value == null || !value.startsWith("C-"))
            throw new IllegalArgumentException("customer id");
    }
}
