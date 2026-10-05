package com.example.learning.module.validationbinding.controller;

import com.example.learning.module.validationbinding.service.ValidationDataBindingExperimentService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/validation-binding/validation")
public class ValidationExperimentController {

    private final ValidationDataBindingExperimentService experiments;

    public ValidationExperimentController(ValidationDataBindingExperimentService experiments) {
        this.experiments = experiments;
    }

    /**
     * README: readme/en/menu/1.Foundation/Foundation.md#validation-vs-binding
     * Purpose: Contrast a binding/conversion failure with a successful bind followed by domain validation failure.
     */
    @GetMapping("/binding-vs-validation")
    public Map<String, Object> bindingVsValidation() {
        return experiments.bindingVsValidationDemo();
    }
}
