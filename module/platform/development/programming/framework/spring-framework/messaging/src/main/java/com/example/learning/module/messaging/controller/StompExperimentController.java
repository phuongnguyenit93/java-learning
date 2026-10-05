package com.example.learning.module.messaging.controller;

import com.example.learning.module.messaging.service.MessagingExperimentService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/messaging/stomp")
public class StompExperimentController {

    private final MessagingExperimentService messagingExperimentService;

    public StompExperimentController(MessagingExperimentService messagingExperimentService) {
        this.messagingExperimentService = messagingExperimentService;
    }

    /** README: readme/vi/menu/5.StompMessageFlow/StompMessageFlow.md#application-message-flow */
    @GetMapping("/round-trip")
    public Map<String, Object> roundTrip() throws Exception {
        return messagingExperimentService.stompRoundTrip();
    }
}
