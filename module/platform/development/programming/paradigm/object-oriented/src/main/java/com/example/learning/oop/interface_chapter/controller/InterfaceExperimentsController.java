package com.example.learning.oop.interface_chapter.controller;

import com.example.learning.oop.interface_chapter.service.InterfaceExperimentsService;
import static com.example.learning.oop.interface_chapter.dto.InterfaceExperimentDtos.*;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Thin HTTP adapter: types are JSON views, not business or repository state. */
@RestController
@RequestMapping("/oop/interfaces")
public final class InterfaceExperimentsController {
    private final InterfaceExperimentsService service;

    public InterfaceExperimentsController(InterfaceExperimentsService service) {
        this.service = service;
    }

    @PostMapping("/shipping/quote")
    public ShippingResult shipping(@RequestBody ShippingRequest request) {
        return service.shipping(request);
    }

    @PostMapping("/documents/experiment")
    public DocumentResult documents(@RequestBody DocumentRequest request) {
        return service.documents(request);
    }

    @PostMapping("/checkout/experiment")
    public CheckoutResult checkout(@RequestBody CheckoutRequest request) {
        return service.checkout(request);
    }
}
