package com.example.learning.module.messaging.controller;

import com.example.learning.module.messaging.service.MessagingExperimentService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/messaging/channel")
public class ChannelExperimentController {

    private final MessagingExperimentService messagingExperimentService;

    public ChannelExperimentController(MessagingExperimentService messagingExperimentService) {
        this.messagingExperimentService = messagingExperimentService;
    }

    /** README: readme/vi/menu/2.MessageModel/MessageModel.md#channel-delivery-semantics */
    @GetMapping("/delivery-semantics")
    public Map<String, Object> deliverySemantics() throws InterruptedException {
        return messagingExperimentService.channelDeliverySemantics();
    }
}
