package com.example.learning.dataaccess.experiment.controller;

import com.example.learning.dataaccess.experiment.service.DataAccessExperimentService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/data-access/r2dbc")
public class R2dbcExperimentController {

    private final DataAccessExperimentService experimentService;

    public R2dbcExperimentController(
            DataAccessExperimentService experimentService
    ) {
        this.experimentService = experimentService;
    }

    @GetMapping("/deferred-execution")
    public Map<String, Object> observeR2dbcDeferredExecution() {
        return experimentService.observeR2dbcDeferredExecution();
    }
}
