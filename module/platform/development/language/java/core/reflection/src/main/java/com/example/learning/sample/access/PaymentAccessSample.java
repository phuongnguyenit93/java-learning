package com.example.learning.sample.access;

public class PaymentAccessSample {

    private final String provider;
    private int processedCount;

    public PaymentAccessSample(String provider) {
        this.provider = provider;
    }

    public String pay() {
        processedCount++;
        return "paid";
    }

    private String internalStatus() {
        return provider + ":" + processedCount;
    }
}
