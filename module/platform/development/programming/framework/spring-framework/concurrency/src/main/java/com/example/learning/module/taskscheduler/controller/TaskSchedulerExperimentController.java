package com.example.learning.module.taskscheduler.controller;

import com.example.learning.module.experiment.service.ConcurrencyExperimentService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/spring-scheduler")
public class TaskSchedulerExperimentController {

    private final ConcurrencyExperimentService concurrencyExperimentService;

    public TaskSchedulerExperimentController(
            ConcurrencyExperimentService concurrencyExperimentService
    ) {
        this.concurrencyExperimentService = concurrencyExperimentService;
    }

    /** README: readme/vi/menu/4.TaskScheduler/TaskScheduler.md#scheduler-execution-models */
    @GetMapping("/execution-models")
    public Map<String, Object> executionModels() {
        return concurrencyExperimentService.schedulerExecutionModelsDemo();
    }
}
