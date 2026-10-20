package com.example.learning.oop.design_pitfalls.controller;

import com.example.learning.oop.design_pitfalls.service.DesignPitfallExperimentService;
import static com.example.learning.oop.design_pitfalls.dto.DesignPitfallDtos.*;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** HTTP adapter with no domain state; all work delegated to the service. */
@RestController
@RequestMapping("/oop/design-pitfalls")
public final class DesignPitfallExperimentController {
    private final DesignPitfallExperimentService service;

    public DesignPitfallExperimentController(DesignPitfallExperimentService service) {
        this.service = service;
    }

    @PostMapping("/seats/experiment")
    public SeatsResult seats(@RequestBody SeatsRequest input) {
        return service.seats(input);
    }

    @PostMapping("/mutable-key/experiment")
    public MutableKeyResult mutableKey(@RequestBody MutableKeyRequest input) {
        return service.mutableKey(input);
    }

    @PostMapping("/checkout/refactor")
    public CheckoutResult checkout(@RequestBody CheckoutRequest input) {
        return service.checkout(input);
    }
}
