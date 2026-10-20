package com.example.learning.oop.solid.controller;

import com.example.learning.oop.solid.dto.SolidDtos.*;
import com.example.learning.oop.solid.service.SolidExperimentService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/oop/solid")
public final class SolidExperimentController {
    private final SolidExperimentService service;

    public SolidExperimentController(SolidExperimentService service) {
        this.service = service;
    }

    @PostMapping("/discount/quote")
    public QuoteResult quote(@RequestBody QuoteRequest request) {
        return service.quote(request);
    }

    @PostMapping("/checkout/experiment")
    public ExperimentResult experiment(@RequestBody ExperimentRequest request) {
        return service.experiment(request);
    }
}
