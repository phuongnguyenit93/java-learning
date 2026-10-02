package com.example.learning.module.nativeinteroperability.controller;

import com.example.learning.module.nativeinteroperability.service.NativeInteroperabilityExperimentService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/native-interoperability/arena")
public class ArenaExperimentController {

    private final NativeInteroperabilityExperimentService experimentService;

    public ArenaExperimentController(
            NativeInteroperabilityExperimentService experimentService
    ) {
        this.experimentService = experimentService;
    }

    /** README: readme/vi/menu/7.ArenaLifetime/ArenaLifetime.md#temporal-safety */
    @GetMapping("/temporal-safety")
    public Map<String, Object> temporalSafety() {
        return experimentService.temporalSafetyDemo();
    }

    /** README: readme/vi/menu/7.ArenaLifetime/ArenaLifetime.md#arena-thread-access */
    @GetMapping("/thread-access")
    public Map<String, Object> threadAccess() {
        return experimentService.threadAccessDemo();
    }
}
