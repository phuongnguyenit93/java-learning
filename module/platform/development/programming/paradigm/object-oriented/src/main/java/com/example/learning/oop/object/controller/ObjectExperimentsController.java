package com.example.learning.oop.object.controller;

import com.example.learning.oop.object.dto.ObjectExperimentDto;
import com.example.learning.oop.object.service.ObjectCheckoutService;
import com.example.learning.oop.object.service.ObjectSemanticsService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Thin HTTP adapter: JSON is the REST representation of each OOP experiment. */
@RestController
@RequestMapping("/oop/objects")
public class ObjectExperimentsController {
    private final ObjectSemanticsService semantics;
    private final ObjectCheckoutService checkout;

    public ObjectExperimentsController(ObjectSemanticsService semantics, ObjectCheckoutService checkout) {
        this.semantics = semantics;
        this.checkout = checkout;
    }

    @PostMapping("/references")
    public ObjectExperimentDto.ReferenceResult references(@RequestBody ObjectExperimentDto.ReferenceRequest request) {
        return semantics.references(request);
    }

    @PostMapping("/equality")
    public ObjectExperimentDto.EqualityResult equality(@RequestBody ObjectExperimentDto.EqualityRequest request) {
        return semantics.equality(request);
    }

    @PostMapping("/copies")
    public ObjectExperimentDto.CopyResult copies(@RequestBody ObjectExperimentDto.CopyRequest request) {
        return semantics.copies(request);
    }

    @PostMapping("/absence")
    public ObjectExperimentDto.AbsenceResult absence(@RequestBody ObjectExperimentDto.AbsenceRequest request) {
        return semantics.absence(request);
    }

    @PostMapping("/checkout")
    public ObjectExperimentDto.CheckoutResult checkout(@RequestBody ObjectExperimentDto.CheckoutRequest request) {
        return checkout.simulate(request);
    }

}
