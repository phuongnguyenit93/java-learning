package com.example.learning.module.web.client.controller;

import com.example.learning.module.web.client.service.HttpClientExperimentService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/spring-web/client")
public class HttpClientExperimentController {

    private final HttpClientExperimentService experimentService;

    public HttpClientExperimentController(HttpClientExperimentService experimentService) {
        this.experimentService = experimentService;
    }

    /**
     * README: readme/en/menu/13.SynchronousHttpClients/SynchronousHttpClients.md#status-and-error-handling
     * Purpose: Compare retrieve status handling with exchange, which owns the response decision.
     */
    @GetMapping("/rest-client-status")
    public Map<String, Object> restClientStatus() {
        return experimentService.restClientStatusHandling();
    }

    /**
     * README: readme/en/menu/14.HttpServiceInterfaces/HttpServiceInterfaces.md#synchronous-http-client-adapters
     * Purpose: Execute the same @HttpExchange interface through RestClientAdapter and RestTemplateAdapter.
     */
    @GetMapping("/http-service-adapters")
    public Map<String, Object> httpServiceAdapters(
            @RequestParam(defaultValue = "42") String id
    ) {
        return experimentService.httpServiceAdapters(id);
    }
}
