package com.example.learning.casestudy.domain.group2.case14;

public record Payment14(String reference, long amountMinor) {
    public Payment14 {
        if (reference == null || reference.isBlank() || amountMinor <= 0)
            throw new IllegalArgumentException("Invalid domain payment");
    }
}
