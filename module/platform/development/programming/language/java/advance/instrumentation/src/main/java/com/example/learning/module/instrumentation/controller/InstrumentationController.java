package com.example.learning.module.instrumentation.controller;

import com.example.learning.module.instrumentation.service.InstrumentationExperimentService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/instrumentation")
public class InstrumentationController {

    private final InstrumentationExperimentService instrumentationExperimentService;

    public InstrumentationController(
            InstrumentationExperimentService instrumentationExperimentService
    ) {
        this.instrumentationExperimentService =
                instrumentationExperimentService;
    }

    /** README: readme/vi/menu/5.InstrumentationApi/InstrumentationApi.md#capability-model */
    @GetMapping("/agent-status")
    public Map<String, Object> agentStatus() {
        return instrumentationExperimentService.agentStatus();
    }

    /** README: readme/vi/menu/7.TransformationPipeline/TransformationPipeline.md#retransform-capability-groups */
    @PostMapping("/retransform-groups")
    public Map<String, Object> retransformGroups() {
        return instrumentationExperimentService.retransformGroups();
    }
}
