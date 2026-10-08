package com.example.learning.webruntime.controller;

import com.example.learning.webruntime.experiment.WebRuntimeExperimentService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/spring-boot/web-runtime")
public class WebRuntimeExperimentController {

    private final WebRuntimeExperimentService experimentService;

    public WebRuntimeExperimentController(WebRuntimeExperimentService experimentService) {
        this.experimentService = experimentService;
    }

    @GetMapping("/server")
    public Map<String, Object> embeddedServer() {
        return experimentService.embeddedServer();
    }

    @GetMapping("/server-properties")
    public Map<String, Object> serverProperties() {
        return experimentService.serverProperties();
    }

    @GetMapping("/request-metadata")
    public Map<String, Object> requestMetadata() {
        return experimentService.requestMetadata();
    }
}
