package com.example.learning.corecontainer.experiment.controller;

import com.example.learning.corecontainer.experiment.model.DependencyFailureScenario;
import com.example.learning.corecontainer.experiment.service.CoreContainerExperimentService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/core-container/dependency")
public class DependencyFailureExperimentController {

    private final CoreContainerExperimentService experimentService;

    public DependencyFailureExperimentController(
            CoreContainerExperimentService experimentService
    ) {
        this.experimentService = experimentService;
    }

    @GetMapping("/failure")
    public Map<String, Object> observeDependencyFailure(
            @RequestParam(defaultValue = "MISSING")
            DependencyFailureScenario scenario
    ) {
        return experimentService.observeDependencyFailure(scenario);
    }
}
