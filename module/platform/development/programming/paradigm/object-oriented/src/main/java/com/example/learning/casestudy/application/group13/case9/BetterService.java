package com.example.learning.casestudy.application.group13.case9;

import java.util.concurrent.atomic.AtomicInteger;

public final class BetterService {
    private final AtomicInteger count = new AtomicInteger();
    public void handle(Runnable work){
        count.incrementAndGet(); // Critical operation nhỏ.
        work.run();              // Không giữ monitor trên work.
    }
    public int count(){
        return count.get();
    }
}
