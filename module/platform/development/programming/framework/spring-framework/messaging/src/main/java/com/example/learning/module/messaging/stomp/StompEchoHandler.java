package com.example.learning.module.messaging.stomp;

import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Controller;

@Controller
public class StompEchoHandler {

    private final StompExperimentProbe stompExperimentProbe;

    public StompEchoHandler(StompExperimentProbe stompExperimentProbe) {
        this.stompExperimentProbe = stompExperimentProbe;
    }

    @MessageMapping("/experiment.echo")
    @SendTo("/topic/experiment.echo")
    public String echo(String payload) {
        stompExperimentProbe.record(payload);
        return "echo:" + payload;
    }
}
