package com.example.learning.oop.subtyping.controller;

import com.example.learning.oop.subtyping.dto.SubtypingDtos.*;
import com.example.learning.oop.subtyping.service.SubtypingExperimentService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/oop/subtyping")
public final class SubtypingExperimentController {
    private final SubtypingExperimentService service;

    public SubtypingExperimentController(SubtypingExperimentService service) {
        this.service = service;
    }

    @PostMapping("/dispatch")
    public DispatchResult dispatch(@RequestBody DispatchRequest request) {
        return service.dispatch(request);
    }

    @PostMapping("/discount-contract")
    public DiscountResult contract(@RequestBody DiscountRequest request) {
        return service.discountContract(request);
    }

    @PostMapping("/geometry-contract")
    public GeometryResult geometry(@RequestBody GeometryRequest request) {
        return service.geometryContract(request);
    }
}
