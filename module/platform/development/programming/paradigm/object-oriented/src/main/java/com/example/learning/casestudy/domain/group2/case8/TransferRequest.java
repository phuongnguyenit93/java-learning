package com.example.learning.casestudy.domain.group2.case8;

public record TransferRequest(String destination, long amount) {
    public TransferRequest {
        if (destination == null || destination.isBlank() || amount <= 0)
            throw new IllegalArgumentException("Invalid transfer");
    }
}
