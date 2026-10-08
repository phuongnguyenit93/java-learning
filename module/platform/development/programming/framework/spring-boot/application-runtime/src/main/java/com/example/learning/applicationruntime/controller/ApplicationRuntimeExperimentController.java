package com.example.learning.applicationruntime.controller;

import com.example.learning.applicationruntime.experiment.ApplicationRuntimeExperimentService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/spring-boot/runtime")
public class ApplicationRuntimeExperimentController {

    private final ApplicationRuntimeExperimentService experimentService;

    public ApplicationRuntimeExperimentController(ApplicationRuntimeExperimentService experimentService) {
        this.experimentService = experimentService;
    }

    @GetMapping("/lifecycle")
    public Map<String, Object> lifecycleTimeline() {
        return experimentService.lifecycleTimeline();
    }

    @GetMapping("/arguments")
    public Map<String, Object> applicationArguments() {
        return experimentService.applicationArguments();
    }

    @GetMapping("/task-executor")
    public Map<String, Object> taskExecutor() {
        return experimentService.taskExecutor();
    }
}
