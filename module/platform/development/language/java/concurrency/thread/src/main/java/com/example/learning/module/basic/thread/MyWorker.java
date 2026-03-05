package com.example.learning.module.basic.thread;

public class MyWorker extends Thread {
    @Override
    public void run() {
        System.out.println("Thread [Extends] đang chạy: " + Thread.currentThread().getName());
    }
}
