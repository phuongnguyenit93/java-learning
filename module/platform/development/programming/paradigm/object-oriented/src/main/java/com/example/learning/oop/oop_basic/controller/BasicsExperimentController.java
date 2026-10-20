package com.example.learning.oop.oop_basic.controller;

import com.example.learning.oop.oop_basic.dto.BasicOopDtos.AccountSimulationRequest;
import com.example.learning.oop.oop_basic.dto.BasicOopDtos.AccountSimulationResponse;
import com.example.learning.oop.oop_basic.dto.BasicOopDtos.OrderSimulationRequest;
import com.example.learning.oop.oop_basic.dto.BasicOopDtos.OrderSimulationResponse;
import com.example.learning.oop.oop_basic.dto.BasicOopDtos.ShippingQuoteRequest;
import com.example.learning.oop.oop_basic.dto.BasicOopDtos.ShippingQuoteResponse;
import com.example.learning.oop.oop_basic.service.BasicsExperimentService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/oop/basics")
public final class BasicsExperimentController {
    private final BasicsExperimentService service;

    public BasicsExperimentController(BasicsExperimentService service) {
        this.service = service;
    }

    @PostMapping("/accounts/simulate")
    public AccountSimulationResponse simulateAccount(@RequestBody AccountSimulationRequest request) {
        return service.simulateAccount(request);
    }

    @PostMapping("/orders/simulate")
    public OrderSimulationResponse simulateOrder(@RequestBody OrderSimulationRequest request) {
        return service.simulateOrder(request);
    }

    @PostMapping("/shipping/quotes")
    public ShippingQuoteResponse compareShipping(@RequestBody ShippingQuoteRequest request) {
        return service.compareShipping(request);
    }

}
