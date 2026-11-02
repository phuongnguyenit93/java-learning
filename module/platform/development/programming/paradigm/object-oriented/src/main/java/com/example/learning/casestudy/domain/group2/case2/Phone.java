package com.example.learning.casestudy.domain.group2.case2;

public record Phone(String value) {
    public Phone {
        if (value == null || !value.startsWith("09"))
            throw new IllegalArgumentException("phone");
    }
}
