package com.example.learning.oop.encapsulation.controller;

import com.example.learning.oop.encapsulation.service.EncapsulationExperimentService;
import static com.example.learning.oop.encapsulation.dto.EncapsulationDtos.*;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** HTTP adapter only; domain rules and state transitions are owned by model/service. */
@RestController
@RequestMapping("/oop/encapsulation")
public final class EncapsulationExperimentController {
    private final EncapsulationExperimentService service;

    public EncapsulationExperimentController(EncapsulationExperimentService service) {
        this.service = service;
    }

    @PostMapping("/cart/experiment")
    public CartResult cart(@RequestBody CartRequest request) {
        return service.simulateCart(request);
    }

    @PostMapping("/stock/experiment")
    public StockResult stock(@RequestBody StockRequest request) {
        return service.simulateStock(request);
    }

    @PostMapping("/event-snapshot/experiment")
    public EventResult snapshot(@RequestBody EventRequest request) {
        return service.simulateEvent(request);
    }

    @PostMapping("/order/experiment")
    public OrderResult order(@RequestBody OrderRequest request) {
        return service.simulateOrder(request);
    }
}
