package com.example.learning.service;

import com.example.learning.model.ControlCommand;
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

    @KafkaListener(topics = "consumer-control")
    public void accountantControlPoint(ControlCommand cmd) {
        if (!cmd.getAppName().equals(appName)) {
            return;
        }

        if (cmd.getListenerId() != null) {
            MessageListenerContainer c =
                    registry.getListenerContainer(cmd.getListenerId());

            switch (cmd.getAction()) {
                case "PAUSE" -> c.pause();
                case "RESUME" -> c.resume();
                case "STOP" -> c.stop();
                case "START" -> c.start();
            }
        }
    }

}
