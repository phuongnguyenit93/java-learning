package com.example.learning.oop.testing.controller;

import com.example.learning.oop.testing.dto.TestingDtos.*;
import com.example.learning.oop.testing.service.CollaborationExperimentService;
import com.example.learning.oop.testing.service.InventoryContractService;
import com.example.learning.oop.testing.service.RegressionExperimentService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/oop/testing")
public final class TestingExperimentController {
    private final InventoryContractService contracts;
    private final CollaborationExperimentService collaborations;
    private final RegressionExperimentService regression;

    public TestingExperimentController(InventoryContractService contracts,
                                       CollaborationExperimentService collaborations,
                                       RegressionExperimentService regression) {
        this.contracts = contracts;
        this.collaborations = collaborations;
        this.regression = regression;
    }

    @PostMapping("/inventory/contracts")
    public ContractResult contracts(@RequestBody ContractRequest request) {
        return contracts.run(request);
    }

    @PostMapping("/checkout/collaboration")
    public CollaborationResult collaboration(@RequestBody CollaborationRequest request) {
        return collaborations.run(request);
    }

    @PostMapping("/pricing/regression")
    public RegressionResult regression(@RequestBody RegressionRequest request) {
        return regression.run(request);
    }
}
