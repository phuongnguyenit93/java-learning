package com.example.learning.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class ConsumerAccountant {
    @Autowired
    KafkaTemplate<String,String> kafkaTemplate;

    @KafkaListener(id="deposit-accountant",topics = "deposit-money-event",groupId = "accountant-group")
    public void accountantListenDeposit(String money) {
        kafkaTemplate
            .send("general-message","accountant","A new customer has deposit : " + money)
            .whenComplete((result,exception) ->{
                System.out.println("A new customer has deposit : " + money);
                    }
            );
    }

    @KafkaListener(id="withdraw-accountant",topics = "withdraw-money-event",groupId = "accountant-group")
    public void accountantListenWithdraw(String money) {
        kafkaTemplate
            .send("general-message","accountant","A new customer has withdraw : " + money)
            .whenComplete((result,exception) ->{
                System.out.println("A new customer has withdraw : " + money);
                    }
            );
    }
}
