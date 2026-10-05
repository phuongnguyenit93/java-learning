package com.example.learning.dataaccess.experiment.controller;

import com.example.learning.dataaccess.experiment.service.DataAccessExperimentService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/data-access/resource")
public class ResourceParticipationExperimentController {

    private final DataAccessExperimentService experimentService;

    public ResourceParticipationExperimentController(
            DataAccessExperimentService experimentService
    ) {
        this.experimentService = experimentService;
    }

    @GetMapping("/binding-context")
    public Map<String, Object> observeResourceBinding() {
        return experimentService.observeResourceBinding();
    }
}
