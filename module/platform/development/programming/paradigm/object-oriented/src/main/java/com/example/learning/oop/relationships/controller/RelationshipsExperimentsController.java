package com.example.learning.oop.relationships.controller;

import com.example.learning.oop.relationships.dto.RelationshipDtos.EnrollmentRequest;
import com.example.learning.oop.relationships.dto.RelationshipDtos.EnrollmentResponse;
import com.example.learning.oop.relationships.dto.RelationshipDtos.OwnershipRequest;
import com.example.learning.oop.relationships.dto.RelationshipDtos.OwnershipResponse;
import com.example.learning.oop.relationships.dto.RelationshipDtos.TransferRequest;
import com.example.learning.oop.relationships.dto.RelationshipDtos.TransferResponse;
import com.example.learning.oop.relationships.service.DepartmentTransferService;
import com.example.learning.oop.relationships.service.EnrollmentScenarioService;
import com.example.learning.oop.relationships.service.OwnershipComparisonService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Input and output are JSON; relational domain behavior lives in models and services. */
@RestController
@RequestMapping("/oop/relationships")
public final class RelationshipsExperimentsController {
    private final DepartmentTransferService transfers;
    private final EnrollmentScenarioService enrollments;
    private final OwnershipComparisonService ownership;

    public RelationshipsExperimentsController(DepartmentTransferService transfers,
                                              EnrollmentScenarioService enrollments,
                                              OwnershipComparisonService ownership) {
        this.transfers = transfers;
        this.enrollments = enrollments;
        this.ownership = ownership;
    }

    @PostMapping("/departments/transfer")
    public TransferResponse transfer(@RequestBody TransferRequest request) {
        return transfers.simulate(request);
    }

    @PostMapping("/enrollments/simulate")
    public EnrollmentResponse enroll(@RequestBody EnrollmentRequest request) {
        return enrollments.simulate(request);
    }

    @PostMapping("/ownership/compare")
    public OwnershipResponse ownership(@RequestBody OwnershipRequest request) {
        return ownership.compare(request);
    }
}
