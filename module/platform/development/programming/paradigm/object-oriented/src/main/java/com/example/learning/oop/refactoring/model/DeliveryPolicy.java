package com.example.learning.oop.refactoring.model;

/** Small behavioral interface replaces service-owned type switching. */
public interface DeliveryPolicy {
    DeliveryQuote quote(long orderValueVnd, int weightKg);
}
