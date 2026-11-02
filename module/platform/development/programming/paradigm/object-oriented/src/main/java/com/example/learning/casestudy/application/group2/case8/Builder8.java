package com.example.learning.casestudy.application.group2.case8;

import com.example.learning.casestudy.domain.group2.case8.TransferRequest;

public final class Builder8 {
    private String destination;
    private long amount;
    public Builder8 destination(String value) {
        destination = value;
        return this;
    }
    public Builder8 amount(long value) {
        amount = value;
        return this;
    }
    public TransferRequest build() {
        if (destination == null || destination.isBlank() || amount <= 0)
            throw new IllegalStateException("Missing transfer fields");
        return new TransferRequest(destination, amount);
    }
}
