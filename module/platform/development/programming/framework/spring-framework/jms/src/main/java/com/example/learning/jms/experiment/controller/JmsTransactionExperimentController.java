package com.example.learning.jms.experiment.controller;

import com.example.learning.jms.experiment.service.JmsExperimentService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/jms/transactions")
public class JmsTransactionExperimentController {

    private final JmsExperimentService experimentService;

    public JmsTransactionExperimentController(
            JmsExperimentService experimentService
    ) {
        this.experimentService = experimentService;
    }

    @GetMapping("/rollback-redelivery")
    public Map<String, Object> observeRollbackRedelivery() {
        return experimentService.observeRollbackRedelivery();
    }
}
