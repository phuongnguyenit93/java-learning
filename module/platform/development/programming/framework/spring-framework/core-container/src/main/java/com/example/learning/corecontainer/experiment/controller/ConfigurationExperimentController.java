package com.example.learning.corecontainer.experiment.controller;

import com.example.learning.corecontainer.experiment.service.CoreContainerExperimentService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/core-container/configuration")
public class ConfigurationExperimentController {

    private final CoreContainerExperimentService experimentService;

    public ConfigurationExperimentController(
            CoreContainerExperimentService experimentService
    ) {
        this.experimentService = experimentService;
    }

    @GetMapping("/full-vs-lite")
    public Map<String, Object> observeConfigurationIdentity() {
        return experimentService.observeConfigurationIdentity();
    }
}
