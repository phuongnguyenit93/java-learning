package com.example.learning.casestudy.model.group3.case7;

public class Bad7 {
    public long sent;
    public void web(long a) {
        if (a>1000)throw new IllegalArgumentException();
        sent += a;
    }
    public void batch(long a) {
        sent += a;
    }
}
