package com.example.learning.module.coordination.modern.service;

import org.springframework.stereotype.Service;

import java.util.concurrent.locks.LockSupport;

@Service
public class LockSupportService {
    public void lockSupportExample() throws InterruptedException {
        Thread t1 = new Thread(() -> {
            System.out.println("T1 đang ngủ...");
            LockSupport.park();
            System.out.println("T1 đã thức giấc!");
        }, "Thread-1");

        Thread t2 = new Thread(() -> {
            System.out.println("T2 đang ngủ...");
            LockSupport.park();
            System.out.println("T2 đã thức giấc!");
        }, "Thread-2");

        t1.start();
        t2.start();

        Thread.sleep(1000);

        System.out.println("Main: Chỉ muốn đánh thức T2 thôi...");
        LockSupport.unpark(t2); // Chỉ định đích danh T2

        Thread.sleep(1000);
        System.out.println("Main: Giờ mới đánh thức T1...");
        LockSupport.unpark(t1);
    }
}
