package com.example.learning.module.nativeinteroperability.controller;

import com.example.learning.module.nativeinteroperability.service.NativeInteroperabilityExperimentService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/native-interoperability/linker")
public class LinkerExperimentController {

    private final NativeInteroperabilityExperimentService experimentService;

    public LinkerExperimentController(
            NativeInteroperabilityExperimentService experimentService
    ) {
        this.experimentService = experimentService;
    }

    /** README: readme/vi/menu/10.DowncallUpcall/DowncallUpcall.md#downcall-handle */
    @GetMapping("/strlen")
    public Map<String, Object> nativeStrlen() {
        return experimentService.nativeStrlenDemo();
    }
}
