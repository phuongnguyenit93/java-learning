package com.example.learning.oop.polymorphism.controller;

import com.example.learning.oop.polymorphism.service.PolymorphismExperimentsService;
import static com.example.learning.oop.polymorphism.dto.PolymorphismDtos.*;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** HTTP adapter only; Java dispatch and business state are implemented elsewhere. */
@RestController
@RequestMapping("/oop/polymorphism")
public final class PolymorphismExperimentsController {
    private final PolymorphismExperimentsService service;

    public PolymorphismExperimentsController(PolymorphismExperimentsService service) {
        this.service = service;
    }

    @PostMapping("/dispatch/experiment")
    public DispatchResult dispatch(@RequestBody DispatchRequest request) {
        return service.dispatch(request);
    }

    @PostMapping("/payments/experiment")
    public PaymentsResult payments(@RequestBody PaymentsRequest request) {
        return service.payments(request);
    }
}
