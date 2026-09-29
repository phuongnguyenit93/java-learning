package com.example.learning.service;

import com.example.learning.model.ControlCommand;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class ControlProducer {
    @Autowired
    KafkaTemplate<String,ControlCommand> kafkaTemplate;

    public void controlConsumer (ControlCommand cmd) {
        kafkaTemplate.send("consumer-control",cmd)
            .whenComplete((result,exception) ->{
                System.out.println(String.format("Control send - App name : %s - Listener ID : %s - Action : %s",cmd.getAppName(),cmd.getListenerId(),cmd.getAction()));
            });
    }
}
