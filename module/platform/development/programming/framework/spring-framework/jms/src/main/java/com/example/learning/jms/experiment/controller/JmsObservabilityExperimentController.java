package com.example.learning.jms.experiment.controller;

import com.example.learning.jms.experiment.service.JmsExperimentService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/jms/observability")
public class JmsObservabilityExperimentController {

    private final JmsExperimentService experimentService;

    public JmsObservabilityExperimentController(
            JmsExperimentService experimentService
    ) {
        this.experimentService = experimentService;
    }

    @GetMapping("/publish-process-boundary")
    public Map<String, Object> observePublishProcessBoundary() {
        return experimentService.observePublishProcessBoundary();
    }
}
