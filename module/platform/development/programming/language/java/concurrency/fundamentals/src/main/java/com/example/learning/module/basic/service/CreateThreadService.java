package com.example.learning.module.basic.service;

import com.example.learning.module.basic.thread.MyWorker;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Service
public class CreateThreadService {

    public List<String> createByExtends() throws InterruptedException {
        String callerThread = Thread.currentThread().getName();

        MyWorker worker = new MyWorker("extends-thread-worker");
        try {
            worker.start();
            joinOrFail(worker);
        } finally {
            cleanup(worker);
        }

        return List.of(
                "Caller thread: " + callerThread,
                "MyWorker.run() đã chạy trên thread: " + worker.getName()
        );
    }

    public List<String> createByRunnable() throws InterruptedException {
        String callerThread = Thread.currentThread().getName();

        Runnable task = () -> System.out.println(
                "Runnable.run() đang chạy trên thread: "
                        + Thread.currentThread().getName()
        );

        Thread worker = new Thread(
                task,
                "runnable-thread-worker"
        );

        try {
            worker.start();
            joinOrFail(worker);
        } finally {
            cleanup(worker);
        }

        return List.of(
                "Caller thread: " + callerThread,
                "Runnable.run() đã chạy trên thread: " + worker.getName()
        );
    }

    public List<String> createByCallable()
            throws ExecutionException, InterruptedException {
        String callerThread = Thread.currentThread().getName();

        Callable<String> task = () ->
                "Callable.call() đang chạy trên thread: "
                        + Thread.currentThread().getName();

        FutureTask<String> futureTask = new FutureTask<>(task);

        Thread worker = new Thread(
                futureTask,
                "callable-thread-worker"
        );

        String result;
        try {
            worker.start();
            result = futureTask.get(2, TimeUnit.SECONDS);
            joinOrFail(worker);
        } catch (TimeoutException e) {
            throw new IllegalStateException("Callable worker không hoàn thành đúng thời gian dự kiến.", e);
        } finally {
            cleanup(worker);
        }

        return List.of(
                "Caller thread: " + callerThread,
                result
        );
    }

    private static void joinOrFail(Thread worker) throws InterruptedException {
        worker.join(2_000);
        if (worker.isAlive()) {
            throw new IllegalStateException("Worker không kết thúc đúng thời gian dự kiến: " + worker.getName());
        }
    }

    private static void cleanup(Thread worker) throws InterruptedException {
        if (!worker.isAlive()) return;
        worker.interrupt();
        worker.join(2_000);
        if (worker.isAlive()) {
            throw new IllegalStateException("Worker vẫn còn sống sau cleanup: " + worker.getName());
        }
    }
}
