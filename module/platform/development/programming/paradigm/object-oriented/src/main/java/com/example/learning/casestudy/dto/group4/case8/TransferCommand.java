package com.example.learning.casestudy.dto.group4.case8;

public record TransferCommand(String destination, long amount) {
    public TransferCommand {
        if (destination == null || destination.isBlank() || amount <= 0)
            throw new IllegalArgumentException("invalid command");
    }
}
