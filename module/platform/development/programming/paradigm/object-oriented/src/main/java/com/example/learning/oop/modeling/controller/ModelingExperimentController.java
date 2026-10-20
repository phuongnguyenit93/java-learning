package com.example.learning.oop.modeling.controller;

import com.example.learning.oop.modeling.service.ModelingExperimentService;
import static com.example.learning.oop.modeling.dto.ModelingDtos.*;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** HTTP boundary delegates all object-state and event decisions to Service/domain. */
@RestController
@RequestMapping("/oop/modeling")
public final class ModelingExperimentController {
    private final ModelingExperimentService service;

    public ModelingExperimentController(ModelingExperimentService service) {
        this.service = service;
    }

    @PostMapping("/library/experiment")
    public LibraryResult library(@RequestBody LibraryRequest request) {
        return service.library(request);
    }

    @PostMapping("/orders/experiment")
    public OrderResult orders(@RequestBody OrderRequest request) {
        return service.order(request);
    }
}
