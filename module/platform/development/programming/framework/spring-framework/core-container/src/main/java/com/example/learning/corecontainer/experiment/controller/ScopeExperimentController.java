package com.example.learning.corecontainer.experiment.controller;

import com.example.learning.corecontainer.experiment.service.CoreContainerExperimentService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/core-container/scope")
public class ScopeExperimentController {

    private final CoreContainerExperimentService experimentService;

    public ScopeExperimentController(CoreContainerExperimentService experimentService) {
        this.experimentService = experimentService;
    }

    @GetMapping("/identity")
    public Map<String, Object> observeScopeIdentity() {
        return experimentService.observeScopeIdentity();
    }
}
