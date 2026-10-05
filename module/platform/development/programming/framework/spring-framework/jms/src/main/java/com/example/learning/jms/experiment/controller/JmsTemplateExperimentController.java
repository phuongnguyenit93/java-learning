package com.example.learning.jms.experiment.controller;

import com.example.learning.jms.experiment.service.JmsExperimentService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/jms/template")
public class JmsTemplateExperimentController {

    private final JmsExperimentService experimentService;

    public JmsTemplateExperimentController(
            JmsExperimentService experimentService
    ) {
        this.experimentService = experimentService;
    }

    @GetMapping("/request-reply")
    public Map<String, Object> observeRequestReplyRoundTrip() {
        return experimentService.observeRequestReplyRoundTrip();
    }
}
