package com.example.learning.casestudy.port.group1.case6;

public interface ShippingPolicy {
    boolean accepts(long weightGrams);
    long fee(long weightGrams);
    String label();
}
