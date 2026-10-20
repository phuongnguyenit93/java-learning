package com.example.learning.oop.composition.controller;

import com.example.learning.oop.composition.dto.CompositionDtos.*;
import com.example.learning.oop.composition.service.CompositionExperimentService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/oop/composition")
public final class CompositionExperimentController {
    private final CompositionExperimentService service;

    public CompositionExperimentController(CompositionExperimentService service) {
        this.service = service;
    }

    @PostMapping("/notifications")
    public NotificationResponse notification(@RequestBody NotificationRequest request) {
        return service.notification(request);
    }

    @PostMapping("/policies")
    public PolicyResponse policies(@RequestBody PolicyRequest request) {
        return service.policies(request);
    }

    @PostMapping("/orders")
    public PlaceOrderResponse placeOrder(@RequestBody PlaceOrderRequest request) {
        return service.placeOrder(request);
    }

    @PostMapping("/resources")
    public ResourceResponse resources(@RequestBody ResourceRequest request) {
        return service.resource(request);
    }
}
