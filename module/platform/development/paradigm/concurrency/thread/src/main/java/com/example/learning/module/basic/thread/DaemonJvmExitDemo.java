package com.example.learning.module.basic.thread;

/**
 * Demo này phải chạy như một Java application độc lập.
 *
 * Argument:
 * true  -> worker là daemon thread
 * false -> worker là user thread
 */
public class DaemonJvmExitDemo {

    public static void main(String[] args) throws InterruptedException {
        boolean daemon = args.length == 0 || Boolean.parseBoolean(args[0]);

        Thread worker = new Thread(() -> {
            for (int i = 1; i <= 5; i++) {
                System.out.printf(
                        "Worker step %d/5 | thread=%s | daemon=%s%n",
                        i,
                        Thread.currentThread().getName(),
                        Thread.currentThread().isDaemon()
                );

                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    System.out.println("Worker bị interrupt và kết thúc.");
                    return;
                }
            }

            System.out.println("Worker hoàn thành toàn bộ công việc.");
        }, "daemon-lifecycle-worker");

        worker.setDaemon(daemon);
        worker.start();

        Thread.sleep(700);

        System.out.printf(
                "main() kết thúc | worker daemon=%s | worker state=%s%n",
                worker.isDaemon(),
                worker.getState()
        );
    }
}
