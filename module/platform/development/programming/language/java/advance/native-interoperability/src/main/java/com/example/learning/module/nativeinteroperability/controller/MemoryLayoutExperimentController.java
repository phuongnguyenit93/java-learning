package com.example.learning.module.nativeinteroperability.controller;

import com.example.learning.module.nativeinteroperability.service.NativeInteroperabilityExperimentService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/native-interoperability/memory-layout")
public class MemoryLayoutExperimentController {

    private final NativeInteroperabilityExperimentService experimentService;

    public MemoryLayoutExperimentController(
            NativeInteroperabilityExperimentService experimentService
    ) {
        this.experimentService = experimentService;
    }

    /** README: readme/vi/menu/8.MemoryLayout/MemoryLayout.md#layout-path-access */
    @GetMapping("/struct-offset")
    public Map<String, Object> structOffset() {
        return experimentService.structOffsetDemo();
    }
}
