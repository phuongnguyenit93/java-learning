package com.example.learning.casestudy.model.group13.case5;

public final class BadCounter5 {
    private int value;
    public synchronized int get(){
        return value;
    }
    public synchronized void set(int v){
        value = v;
    }
}
