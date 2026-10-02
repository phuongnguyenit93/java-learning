package com.example.learning.module.nativeinteroperability.controller;

import com.example.learning.module.nativeinteroperability.service.NativeInteroperabilityExperimentService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/native-interoperability/memory-segment")
public class MemorySegmentExperimentController {

    private final NativeInteroperabilityExperimentService experimentService;

    public MemorySegmentExperimentController(
            NativeInteroperabilityExperimentService experimentService
    ) {
        this.experimentService = experimentService;
    }

    /** README: readme/vi/menu/6.MemorySegment/MemorySegment.md#segment-spatial-bounds */
    @GetMapping("/spatial-bounds")
    public Map<String, Object> spatialBounds() {
        return experimentService.spatialBoundsDemo();
    }
}
