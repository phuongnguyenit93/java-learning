package com.example.learning.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class BankProducer {
    @Autowired
    KafkaTemplate<String,String> kafkaTemplate;

    // Producer Method
    public void depositMoney (String money) {
        kafkaTemplate.send("deposit-money-event",money)
            .whenComplete((result,exception) ->{
                System.out.println("Customer has deposit : " + money);
            });
    }

    // Producer Method
    public void withdrawMoney (String money) {
        kafkaTemplate.send("withdraw-money-event",money)
            .whenComplete((result,exception) ->{
                System.out.println("Customer has withdraw : " + money);
            });
    }
}
