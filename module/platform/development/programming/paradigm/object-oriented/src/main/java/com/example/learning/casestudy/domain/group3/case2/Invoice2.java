package com.example.learning.casestudy.domain.group3.case2;

public class Invoice2 {
    private final int subtotal = 100;
    private int discount;
    public void changeDiscount(int d) {
        if (d<0 || d>subtotal)throw new IllegalArgumentException();
        discount = d;
    }
    public int total() {
        return subtotal-discount;
    }
}
