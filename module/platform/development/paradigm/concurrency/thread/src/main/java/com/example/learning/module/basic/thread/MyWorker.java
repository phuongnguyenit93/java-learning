package com.example.learning.module.basic.thread;

public class MyWorker extends Thread {

    public MyWorker(String name) {
        super(name);
    }

    @Override
    public void run() {
        System.out.println(
                "MyWorker.run() đang chạy trên thread: "
                        + Thread.currentThread().getName()
        );
    }
}
