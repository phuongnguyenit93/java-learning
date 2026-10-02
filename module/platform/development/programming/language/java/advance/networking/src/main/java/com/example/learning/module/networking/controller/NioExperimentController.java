package com.example.learning.module.networking.controller;

import com.example.learning.module.networking.service.NetworkingExperimentService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/networking/nio")
public class NioExperimentController {

    private final NetworkingExperimentService networkingExperimentService;

    public NioExperimentController(
            NetworkingExperimentService networkingExperimentService
    ) {
        this.networkingExperimentService = networkingExperimentService;
    }

    /** README: readme/vi/menu/6.NioNetworking/NioNetworking.md#selector-readiness-model */
    @GetMapping("/selector-readiness")
    public Map<String, Object> selectorReadiness() throws IOException {
        return networkingExperimentService.selectorReadinessDemo();
    }
}
