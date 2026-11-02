package com.example.learning.casestudy.model.group3.case2;

public class Bad2 {
    public int subtotal = 100, discount, total = 100;
    public void setDiscount(int d) {
        discount = d;
    }
    public void setTotal(int t) {
        if (t<0)throw new IllegalArgumentException();
        total = t;
    }
}
