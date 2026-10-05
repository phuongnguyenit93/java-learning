package com.example.learning.dataaccess.experiment.controller;

import com.example.learning.dataaccess.experiment.service.DataAccessExperimentService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/data-access/dao-exception")
public class DaoExceptionExperimentController {

    private final DataAccessExperimentService experimentService;

    public DaoExceptionExperimentController(
            DataAccessExperimentService experimentService
    ) {
        this.experimentService = experimentService;
    }

    @GetMapping("/cross-stack-translation")
    public Map<String, Object> observeCrossStackExceptionTranslation() {
        return experimentService.observeCrossStackExceptionTranslation();
    }
}
