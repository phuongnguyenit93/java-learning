package com.example.learning.oop.design_patterns.controller;

import com.example.learning.oop.design_patterns.service.DesignPatternExperimentService;
import static com.example.learning.oop.design_patterns.dto.DesignPatternDtos.*;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Thin HTTP adapter: business decisions reside in service and domain objects. */
@RestController
@RequestMapping("/oop/design-patterns")
public final class DesignPatternExperimentController {
    private final DesignPatternExperimentService service;

    public DesignPatternExperimentController(DesignPatternExperimentService service) {
        this.service = service;
    }

    @PostMapping("/pricing/experiment")
    public PricingResult pricing(@RequestBody PricingRequest request) {
        return service.price(request);
    }

    @PostMapping("/tasks/experiment")
    public TasksResult tasks(@RequestBody TasksRequest request) {
        return service.tasks(request);
    }

    @PostMapping("/reports/generate")
    public ReportResult report(@RequestBody ReportRequest request) {
        return service.report(request);
    }
}
