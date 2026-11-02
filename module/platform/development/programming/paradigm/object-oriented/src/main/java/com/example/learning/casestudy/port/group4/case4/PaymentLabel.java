package com.example.learning.casestudy.port.group4.case4;

public interface PaymentLabel {
    default String label() { return "Payment"; }
}
