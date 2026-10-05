package com.example.learning.module.messaging.controller;

import com.example.learning.module.messaging.service.MessagingExperimentService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/messaging/rsocket")
public class RSocketExperimentController {

    private final MessagingExperimentService messagingExperimentService;

    public RSocketExperimentController(MessagingExperimentService messagingExperimentService) {
        this.messagingExperimentService = messagingExperimentService;
    }

    /** README: readme/vi/menu/12.RSocketResponders/RSocketResponders.md#rsocket-interaction-cardinality */
    @GetMapping("/interaction-cardinality")
    public Map<String, Object> interactionCardinality() {
        return messagingExperimentService.rsocketInteractionCardinality();
    }
}
