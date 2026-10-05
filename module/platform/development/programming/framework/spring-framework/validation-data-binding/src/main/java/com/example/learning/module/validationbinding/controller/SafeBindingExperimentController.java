package com.example.learning.module.validationbinding.controller;

import com.example.learning.module.validationbinding.service.ValidationDataBindingExperimentService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/validation-binding/safe-binding")
public class SafeBindingExperimentController {

    private final ValidationDataBindingExperimentService experiments;

    public SafeBindingExperimentController(ValidationDataBindingExperimentService experiments) {
        this.experiments = experiments;
    }

    /**
     * README: readme/en/menu/8.SafeBinding/SafeBinding.md#safe-binding-controls
     * Purpose: Contrast ordinary property binding with declarative binding before and after an explicit allowed-field surface.
     */
    @GetMapping("/declarative")
    public Map<String, Object> declarativeBinding() {
        return experiments.declarativeBindingDemo();
    }
}
