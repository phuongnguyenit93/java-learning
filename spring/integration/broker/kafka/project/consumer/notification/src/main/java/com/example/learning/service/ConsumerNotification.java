package com.example.learning.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class ConsumerNotification {
    @Autowired
    KafkaTemplate<String,String> kafkaTemplate;

    @KafkaListener(id = "deposit-money",topics = "deposit-money-event",groupId = "notify-group")
    public void accountantListenDeposit(String money) {
        kafkaTemplate
            .send("general-message","notify","This is notification that you has deposit : " + money)
            .whenComplete((result,exception) ->{
                System.out.println("This is notification that you has deposit : " + money);
            });
    }

    @KafkaListener(id = "withdraw-money",topics = "withdraw-money-event",groupId = "notify-group")
    public void accountantListenWithdraw(String money) {
        kafkaTemplate
            .send("general-message","notify","This is notification that you has withdraw : " + money)
            .whenComplete((result,exception) ->{
                System.out.println("This is notification that you has withdraw : " + money);
            });
    }
}
