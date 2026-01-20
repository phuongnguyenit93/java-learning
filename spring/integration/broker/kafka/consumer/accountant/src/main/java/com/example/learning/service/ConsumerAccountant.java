package com.example.learning.service;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
public class ConsumerAccountant {
    @KafkaListener(id="deposit-accountant",topics = "deposit-money-event",groupId = "accountant-group")
    public void accountantListenDeposit(String money) {
        System.out.println("A new customer has deposit : " + money);
    }

    @KafkaListener(id="withdraw-accountant",topics = "withdraw-money-event",groupId = "accountant-group")
    public void accountantListenWithdraw(String money) {
        System.out.println("A new customer has withdraw : " + money);
    }
}
