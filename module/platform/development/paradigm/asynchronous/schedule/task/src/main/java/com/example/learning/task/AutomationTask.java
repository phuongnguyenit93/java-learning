package com.example.learning.task;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
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
    //fixedRate = 1000)
    public void myTask2(){
        try {
            Thread.sleep(2000);
            System.out.println("This task is run after 10 second and every 15 second after this task end or start");
        } catch (Exception e) {
            System.out.println("Failed");
        }
    }
}
