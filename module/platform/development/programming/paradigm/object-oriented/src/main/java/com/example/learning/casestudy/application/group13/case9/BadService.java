package com.example.learning.casestudy.application.group13.case9;

public final class BadService {
    private int count;
    public synchronized void handle(Runnable work){
        count++;
        work.run(); // Lệnh ngoài vẫn giữ monitor của cả service.
    }
    public synchronized int count(){
        return count;
    }
}
