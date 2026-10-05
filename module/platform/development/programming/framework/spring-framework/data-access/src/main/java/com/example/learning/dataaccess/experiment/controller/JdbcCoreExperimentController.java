package com.example.learning.dataaccess.experiment.controller;

import com.example.learning.dataaccess.experiment.service.DataAccessExperimentService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/data-access/jdbc")
public class JdbcCoreExperimentController {

    private final DataAccessExperimentService experimentService;

    public JdbcCoreExperimentController(
            DataAccessExperimentService experimentService
    ) {
        this.experimentService = experimentService;
    }

    @GetMapping("/cardinality")
    public Map<String, Object> observeJdbcCardinality() {
        return experimentService.observeJdbcCardinality();
    }
}
