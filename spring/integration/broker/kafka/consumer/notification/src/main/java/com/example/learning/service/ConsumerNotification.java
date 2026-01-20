package com.example.learning.service;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
public class ConsumerNotification {
    @KafkaListener(id = "deposit-money",topics = "deposit-money-event",groupId = "notify-group")
    public void accountantListenDeposit(String money) {
        System.out.println("This is notification that you has deposit : " + money);
    }

    @KafkaListener(id = "withdraw-money",topics = "withdraw-money-event",groupId = "notify-group")
    public void accountantListenWithdraw(String money) {
        System.out.println("This is notification that you has withdraw : " + money);
    }
}
