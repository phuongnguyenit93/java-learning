package com.example.learning.service;

import com.example.learning.model.ControlCommand;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.stereotype.Service;

@Service
public class ConsumerControlPoint {
    @Value("${spring.application.name}")
    private String appName;

    @Autowired
    KafkaListenerEndpointRegistry registry;

    @Autowired
    ObjectMapper objectMapper;

    @KafkaListener(topics = "consumer-control")
    public void accountantControlPoint(String command) throws JsonProcessingException {
        ControlCommand cmd = objectMapper.readValue(command,ControlCommand.class);

        if (!cmd.getAppName().equals(appName)) {
            return;
        }

        if (cmd.getListenerId() != null) {
            MessageListenerContainer c =
                    registry.getListenerContainer(cmd.getListenerId());
            if (c != null) {
                switch (cmd.getAction()) {
                    case "PAUSE" -> c.pause();
                    case "RESUME" -> c.resume();
                    case "STOP" -> c.stop();
                    case "START" -> c.start();
                }
            }

        }
    }

}
