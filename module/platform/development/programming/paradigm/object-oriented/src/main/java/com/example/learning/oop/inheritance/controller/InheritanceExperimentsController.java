package com.example.learning.oop.inheritance.controller;

import com.example.learning.oop.inheritance.dto.InheritanceExperimentDto.CapabilitiesRequest;
import com.example.learning.oop.inheritance.dto.InheritanceExperimentDto.CapabilitiesResponse;
import com.example.learning.oop.inheritance.dto.InheritanceExperimentDto.ConstructorRequest;
import com.example.learning.oop.inheritance.dto.InheritanceExperimentDto.ConstructorResponse;
import com.example.learning.oop.inheritance.dto.InheritanceExperimentDto.DeliveryRequest;
import com.example.learning.oop.inheritance.dto.InheritanceExperimentDto.DeliveryResponse;
import com.example.learning.oop.inheritance.dto.InheritanceExperimentDto.PayrollRequest;
import com.example.learning.oop.inheritance.dto.InheritanceExperimentDto.PayrollResponse;
import com.example.learning.oop.inheritance.service.CapabilityService;
import com.example.learning.oop.inheritance.service.ConstructorTraceService;
import com.example.learning.oop.inheritance.service.DeliveryComparisonService;
import com.example.learning.oop.inheritance.service.PayrollInheritanceService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/oop/inheritance")
public final class InheritanceExperimentsController {
    private final PayrollInheritanceService payroll;
    private final DeliveryComparisonService delivery;
    private final ConstructorTraceService constructors;
    private final CapabilityService capabilities;

    public InheritanceExperimentsController(
            PayrollInheritanceService payroll, DeliveryComparisonService delivery,
            ConstructorTraceService constructors, CapabilityService capabilities) {
        this.payroll = payroll;
        this.delivery = delivery;
        this.constructors = constructors;
        this.capabilities = capabilities;
    }

    @PostMapping("/payroll/calculate")
    public PayrollResponse calculatePay(@RequestBody PayrollRequest request) {
        return payroll.calculate(request);
    }

    @PostMapping("/delivery/compare")
    public DeliveryResponse compareDelivery(@RequestBody DeliveryRequest request) {
        return delivery.compare(request);
    }

    @PostMapping("/constructors/trace")
    public ConstructorResponse constructorTrace(@RequestBody ConstructorRequest request) {
        return constructors.trace(request);
    }

    @PostMapping("/capabilities/resolve")
    public CapabilitiesResponse resolveCapabilities(@RequestBody CapabilitiesRequest request) {
        return capabilities.resolve(request);
    }
}
