package com.example.learning.corecontainer.experiment.controller;

import com.example.learning.corecontainer.experiment.service.CoreContainerExperimentService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/core-container/lifecycle")
public class LifecycleExperimentController {

    private final CoreContainerExperimentService experimentService;

    public LifecycleExperimentController(CoreContainerExperimentService experimentService) {
        this.experimentService = experimentService;
    }

    @GetMapping("/callbacks")
    public Map<String, Object> observeLifecycleCallbacks() {
        return experimentService.observeLifecycleCallbacks();
    }
}
