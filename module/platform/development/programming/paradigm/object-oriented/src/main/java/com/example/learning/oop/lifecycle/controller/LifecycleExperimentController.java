package com.example.learning.oop.lifecycle.controller;

import com.example.learning.oop.lifecycle.dto.LifecycleDtos.CreationRequest;
import com.example.learning.oop.lifecycle.dto.LifecycleDtos.CreationResult;
import com.example.learning.oop.lifecycle.dto.LifecycleDtos.PublicationRequest;
import com.example.learning.oop.lifecycle.dto.LifecycleDtos.PublicationResult;
import com.example.learning.oop.lifecycle.dto.LifecycleDtos.ReferenceRequest;
import com.example.learning.oop.lifecycle.dto.LifecycleDtos.ReferenceResult;
import com.example.learning.oop.lifecycle.dto.LifecycleDtos.ResourceRequest;
import com.example.learning.oop.lifecycle.dto.LifecycleDtos.ResourceResult;
import com.example.learning.oop.lifecycle.service.LifecycleExperimentService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/oop/lifecycle")
public final class LifecycleExperimentController {
    private final LifecycleExperimentService service;

    public LifecycleExperimentController(LifecycleExperimentService service) {
        this.service = service;
    }

    @PostMapping("/creation")
    public CreationResult create(@RequestBody CreationRequest request) {
        return service.create(request);
    }

    @PostMapping("/references")
    public ReferenceResult references(@RequestBody ReferenceRequest request) {
        return service.references(request);
    }

    @PostMapping("/resources")
    public ResourceResult resources(@RequestBody ResourceRequest request) {
        return service.resources(request);
    }

    @PostMapping("/publication")
    public PublicationResult publication(@RequestBody PublicationRequest request) {
        return service.publication(request);
    }
}
