package com.example.learning.casestudy.domain.group13.case5;

public final class Counter {
    private int value;
    public synchronized void increment(){
        value++;
    }
    public synchronized int get(){
        return value;
    }
}
