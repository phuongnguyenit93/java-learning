package com.example.learning.service;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
public class ControlService {
    // Concurrency = so consumer , luu y so consumer <= so partition
    @KafkaListener(topics = "general-message",groupId = "control-group",concurrency = "3",autoStartup = "true")
    public void generalMessage(String message) {
        System.out.println(String.format("General Message: %s",message));
    }
}
