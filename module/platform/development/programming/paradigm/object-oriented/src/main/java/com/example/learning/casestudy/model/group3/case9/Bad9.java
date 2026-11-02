package com.example.learning.casestudy.model.group3.case9;

public class Bad9 {
    public volatile int subtotal = 100, discount = 0;
    public void firstStep() {
        discount = 20;
    }
    public void secondStep() {
        subtotal = 120;
    }
}
