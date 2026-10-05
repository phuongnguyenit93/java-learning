package com.example.learning.module.scheduling;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Profile;

@Component
@Profile("legacy-scheduling-demo")
public class AutomationTask {
    @Scheduled(cron = "0 * * * * *", zone = "GMT+7")
    public void automationTask1() {
        System.out.println("This task run every minute at second 00");
    }

    // initialDelay : Thời điểm delay khởi đầu
    // fixedDelay : Tần suất tính theo thời điểm task này end (Lệ thuộc time execute của task)
    // fixedRate : Tần suất tính theo thời điểm bắt đầu của 2 task (Không lệ thuộc time)
    @Scheduled(initialDelay = 10000,
            fixedDelay = 15000)
    public void myTask2() {
        try {
            Thread.sleep(2000);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Scheduled demo was interrupted", error);
        }
        System.out.println("This task runs after 10 seconds and then 15 seconds after each execution completes");
    }
}
