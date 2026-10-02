package com.example.learning.module.networking.controller;

import com.example.learning.module.networking.service.NetworkingExperimentService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/networking/failure")
public class FailureExperimentController {

    private final NetworkingExperimentService networkingExperimentService;

    public FailureExperimentController(
            NetworkingExperimentService networkingExperimentService
    ) {
        this.networkingExperimentService = networkingExperimentService;
    }

    /** README: readme/vi/menu/5.FailureControl/FailureControl.md#socket-timeouts */
    @GetMapping("/read-timeout")
    public Map<String, Object> readTimeout() throws IOException {
        return networkingExperimentService.readTimeoutDemo();
    }
}
